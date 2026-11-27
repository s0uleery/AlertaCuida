-- Flujo conversacional de WhatsApp Business (v3), cierre parcial pendiente de validación PRLAC y aviso FCM
-- genérico de cambio de estado (decisiones del 26-11-2026).

-- 1. Aviso FCM de cambio de estado: repetible por alerta (cada cambio genera su propio id_aviso para acuse y
--    deduplicación en el reloj, que consulta GET /api/reloj/v1/alertas/{eventId}). Los tipos históricos se
--    conservan únicos para compatibilidad con datos y pruebas existentes.
ALTER TABLE aviso_fcm
	DROP CONSTRAINT aviso_fcm_tipo_check,
	ADD CONSTRAINT aviso_fcm_tipo_check
		CHECK (tipo IN ('ALERTA_CONFIRMADA', 'ALERTA_RESUELTA', 'SIMULACRO', 'CAMBIO_ESTADO_ALERTA')),
	DROP CONSTRAINT aviso_fcm_id_alerta_tipo_key;

CREATE UNIQUE INDEX aviso_fcm_unico_no_cambio ON aviso_fcm (id_alerta, tipo)
	WHERE tipo <> 'CAMBIO_ESTADO_ALERTA';

-- 2. Resultado reportado por WhatsApp en un cierre parcial (pendiente de validación definitiva por PRLAC en
--    el dashboard mediante "Completar cierre").
ALTER TABLE atencion_alerta
	ADD COLUMN resultado_cierre_whatsapp varchar
		CHECK (resultado_cierre_whatsapp IN ('FALSA_ALARMA', 'APOYO_NO_URGENTE', 'EMERGENCIA'));

-- 3. Estado conversacional por intento de WhatsApp (solo identificadores, estados y marcas de tiempo; nunca
--    guarda contenido crudo de webhooks ni datos personales en claro).
CREATE TABLE conversacion_whatsapp (
	id_conversacion uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_intento uuid NOT NULL UNIQUE REFERENCES registro_intento (id_intento) ON DELETE CASCADE,
	id_alerta uuid NOT NULL REFERENCES alerta (id_alerta) ON DELETE CASCADE,
	nivel varchar NOT NULL CHECK (nivel IN ('NIVEL_1_PRLAC', 'NIVEL_2_TERCERO', 'NIVEL_3_SERVICIO', 'SIMULACRO')),
	etapa varchar NOT NULL CHECK (etapa IN ('ESPERANDO_ACEPTACION', 'ESPERANDO_ESTADO_LLAMADA',
		'ESPERANDO_DISPONIBILIDAD', 'ESPERANDO_LLEGADA', 'ESPERANDO_RESULTADO_VISITA',
		'COMPLETADA', 'RECHAZADA', 'EXPIRADA', 'DESCARTADA')),
	ultimo_wamid varchar,
	recordatorios_enviados int NOT NULL DEFAULT 0 CHECK (recordatorios_enviados >= 0),
	fecha_hora_proximo_vencimiento timestamptz,
	ventana_24h_hasta timestamptz,
	fecha_hora_creacion timestamptz NOT NULL DEFAULT now(),
	fecha_hora_actualizacion timestamptz NOT NULL DEFAULT now()
);

-- Solo un contacto puede tener tomada activamente la conversación de una alerta a la vez: evita carreras si
-- dos contactos presionan el botón inicial en el mismo instante.
CREATE UNIQUE INDEX conversacion_whatsapp_activa_por_alerta ON conversacion_whatsapp (id_alerta)
	WHERE etapa IN ('ESPERANDO_ESTADO_LLAMADA', 'ESPERANDO_DISPONIBILIDAD',
		'ESPERANDO_LLEGADA', 'ESPERANDO_RESULTADO_VISITA');

CREATE INDEX conversacion_whatsapp_wamid ON conversacion_whatsapp (ultimo_wamid)
	WHERE ultimo_wamid IS NOT NULL;

CREATE INDEX conversacion_whatsapp_vencimiento ON conversacion_whatsapp (fecha_hora_proximo_vencimiento)
	WHERE fecha_hora_proximo_vencimiento IS NOT NULL;

CREATE INDEX conversacion_whatsapp_alerta ON conversacion_whatsapp (id_alerta, etapa);

-- 4. Bandeja de salida desacoplada para respuestas interactivas y de texto dentro de la ventana de 24 h:
--    el webhook responde 200 OK de inmediato y estos mensajes se envían fuera de la transacción HTTP.
CREATE TABLE mensaje_whatsapp_saliente (
	id_mensaje uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_conversacion uuid NOT NULL REFERENCES conversacion_whatsapp (id_conversacion) ON DELETE CASCADE,
	tipo varchar NOT NULL CHECK (tipo IN ('TEXTO', 'INTERACTIVO_BOTONES')),
	cuerpo text NOT NULL,
	botones jsonb,
	reintentos int NOT NULL DEFAULT 0 CHECK (reintentos >= 0),
	fecha_hora_proximo_envio timestamptz,
	fecha_hora_envio timestamptz,
	id_externo varchar UNIQUE,
	fecha_hora_fallo timestamptz,
	motivo_fallo varchar,
	CONSTRAINT mensaje_saliente_botones CHECK ((tipo = 'TEXTO' AND botones IS NULL)
		OR (tipo = 'INTERACTIVO_BOTONES' AND botones IS NOT NULL)),
	CONSTRAINT mensaje_saliente_pendiente CHECK (fecha_hora_proximo_envio IS NULL
		OR (fecha_hora_envio IS NULL AND fecha_hora_fallo IS NULL))
);

CREATE INDEX mensaje_whatsapp_saliente_pendiente ON mensaje_whatsapp_saliente (fecha_hora_proximo_envio)
	WHERE fecha_hora_proximo_envio IS NOT NULL;

-- 5. Deduplicación de mensajes entrantes de Meta (entrega at-least-once por messages[].id).
--    No tiene clave foránea porque registra también mensajes que pueden ignorarse o no corresponder a un aviso.
CREATE TABLE webhook_whatsapp_procesado (
	id_mensaje_meta varchar PRIMARY KEY,
	fecha_hora_recepcion timestamptz NOT NULL DEFAULT now()
);

-- 6. Vista alerta_estado actualizada: una conversación de WhatsApp tomada (esperando estado de llamada,
--    disponibilidad, llegada o resultado) pausa al reloj en estado CONFIRMADA; si se rechaza o expira,
--    vuelve a AVISANDO_CONTACTO para que el reloj continúe escalando. Un cierre parcial deja fecha_hora_cierre
--    y el reloj ve RESUELTA.
CREATE OR REPLACE VIEW alerta_estado AS
SELECT a.id_alerta,
	CASE
		WHEN a.fecha_hora_cancelacion IS NOT NULL THEN 'CANCELADA'
		WHEN t.fecha_hora_cierre IS NOT NULL THEN 'RESUELTA'
		WHEN EXISTS (SELECT 1 FROM conversacion_whatsapp cw
				WHERE cw.id_alerta = a.id_alerta
				AND cw.etapa IN ('ESPERANDO_ESTADO_LLAMADA', 'ESPERANDO_DISPONIBILIDAD',
					'ESPERANDO_LLEGADA', 'ESPERANDO_RESULTADO_VISITA'))
			OR EXISTS (SELECT 1 FROM registro_intento i
				WHERE i.id_alerta = a.id_alerta AND i.respuesta = 'VOY_EN_CAMINO'
				AND NOT EXISTS (SELECT 1 FROM conversacion_whatsapp cw WHERE cw.id_intento = i.id_intento))
			OR t.resultado_llamada = 'BIEN' OR t.fecha_hora_personal_asignado IS NOT NULL THEN 'CONFIRMADA'
		WHEN a.fecha_hora_sin_respuesta IS NOT NULL THEN 'SIN_RESPUESTA'
		WHEN EXISTS (SELECT 1 FROM registro_intento i
			WHERE i.id_alerta = a.id_alerta AND i.id_contacto_paciente IS NOT NULL) THEN 'AVISANDO_CONTACTO'
		WHEN a.espera_dia_habil THEN 'PENDIENTE_DIA_HABIL'
		ELSE 'RECIBIDA'
	END AS estado_contacto,
	CASE
		WHEN t.clasificacion = 'POSITIVA' AND t.fecha_cierre_seguimiento IS NULL THEN 'EN_SEGUIMIENTO'
		WHEN t.fecha_hora_cierre IS NOT NULL THEN 'RESUELTA'
		WHEN t.fecha_hora_toma IS NOT NULL THEN 'EN_ATENCION'
		ELSE 'SIN_ASIGNAR'
	END AS estado_atencion,
	CASE WHEN a.fecha_hora_agravamiento IS NOT NULL THEN 'GRAVE' ELSE a.severidad END AS severidad_actual
FROM alerta a
LEFT JOIN atencion_alerta t USING (id_alerta);
