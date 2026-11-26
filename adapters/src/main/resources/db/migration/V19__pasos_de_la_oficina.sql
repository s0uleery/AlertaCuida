-- Pasos de la oficina PRLAC en la página del enlace del correo, iguales a los mensajes de WhatsApp N1-02 a
-- N1-11 (decisiones del 26-11-2026): tomar la alerta, llamar al paciente, coordinar la visita del personal,
-- registrar su llegada y el resultado, y cerrar.
--
-- Los pasos son de la alerta, no de cada aviso: un coordinador puede tener varios enlaces de la misma alerta
-- (aviso a la oficina, agravamiento, recordatorio) y todos muestran la misma etapa. Por eso van en
-- atencion_alerta, donde ya están el responsable (quien la tomó) y el cierre. La etapa no se guarda: se calcula
-- con estas fechas.

ALTER TABLE atencion_alerta
	-- Lo que pasó al llamar al reloj del paciente
	ADD COLUMN resultado_llamada varchar CHECK (resultado_llamada IN ('BIEN', 'NECESITA_ATENCION', 'NO_CONTESTA')),
	ADD COLUMN fecha_hora_resultado_llamada timestamptz,
	-- Necesita atención o no contesta: la oficina asignó a alguien para ir, o no tenía a nadie
	ADD COLUMN fecha_hora_personal_asignado timestamptz,
	ADD COLUMN fecha_hora_sin_disponibilidad timestamptz,
	-- El personal avisó que llegó, y cómo encontró a la persona
	ADD COLUMN fecha_hora_llegada_personal timestamptz,
	ADD COLUMN resultado_visita varchar CHECK (resultado_visita IN ('BIEN', 'APOYO_NO_URGENTE', 'EMERGENCIA')),
	ADD COLUMN fecha_hora_resultado_visita timestamptz,
	-- Los pasos los da quien tiene la alerta a cargo
	ADD CONSTRAINT atencion_llamada CHECK ((resultado_llamada IS NULL) = (fecha_hora_resultado_llamada IS NULL)
		AND (resultado_llamada IS NULL OR fecha_hora_toma IS NOT NULL)),
	-- Solo se coordina la visita si la persona necesita atención o no contestó, y es una cosa o la otra
	ADD CONSTRAINT atencion_visita_coordinada CHECK (
		(fecha_hora_personal_asignado IS NULL AND fecha_hora_sin_disponibilidad IS NULL)
		OR (resultado_llamada IN ('NECESITA_ATENCION', 'NO_CONTESTA')
			AND (fecha_hora_personal_asignado IS NULL OR fecha_hora_sin_disponibilidad IS NULL))),
	-- Llega quien fue asignado, y el resultado es de esa visita
	ADD CONSTRAINT atencion_llegada_personal CHECK (fecha_hora_llegada_personal IS NULL
		OR fecha_hora_personal_asignado IS NOT NULL),
	ADD CONSTRAINT atencion_resultado_visita CHECK ((resultado_visita IS NULL) = (fecha_hora_resultado_visita IS NULL)
		AND (resultado_visita IS NULL OR fecha_hora_llegada_personal IS NOT NULL));

-- Página del contacto del paciente: después de "Llamar al paciente" dice si le contestaron (26-11-2026)
ALTER TABLE registro_intento
	ADD COLUMN resultado_llamada varchar CHECK (resultado_llamada IN ('CONTESTA', 'NO_CONTESTA')),
	ADD COLUMN fecha_hora_resultado_llamada timestamptz,
	ADD CONSTRAINT intento_resultado_llamada CHECK ((resultado_llamada IS NULL) = (fecha_hora_resultado_llamada IS NULL)
		AND (resultado_llamada IS NULL OR id_contacto_paciente IS NOT NULL)),
	-- Correo al responsable cuando el personal asignado lleva un rato sin llegar (N1-08), una vez por alerta.
	-- Que exista esta fila es lo que dice que ya se envió.
	DROP CONSTRAINT registro_intento_proposito_check,
	ADD CONSTRAINT registro_intento_proposito_check
		CHECK (proposito IN ('AVISO', 'VIGILANTE', 'AGRAVAMIENTO', 'RECORDATORIO'));

-- La alerta queda confirmada (el reloj deja de avisar) cuando un contacto dice "Voy en camino" y también
-- cuando la oficina sabe que la persona está bien o asignó personal para ir. "Tomo la alerta" no la confirma:
-- si la oficina después no tiene a nadie, el reloj sigue avisando a los contactos.
CREATE OR REPLACE VIEW alerta_estado AS
SELECT a.id_alerta,
	CASE
		WHEN a.fecha_hora_cancelacion IS NOT NULL THEN 'CANCELADA'
		WHEN t.fecha_hora_cierre IS NOT NULL THEN 'RESUELTA'
		WHEN EXISTS (SELECT 1 FROM registro_intento i
			WHERE i.id_alerta = a.id_alerta AND i.respuesta = 'VOY_EN_CAMINO')
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
