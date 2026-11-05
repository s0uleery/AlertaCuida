-- Zona segura por paciente, botón de ayuda en el reloj y WhatsApp para las cuentas del equipo.

-- Zona segura: un círculo por paciente (centro y radio), con su propio historial, como el umbral. Cambiar el
-- radio no agrega una versión de la configuración. Las coordenadas van cifradas: suelen ser el domicilio.
-- La revisa el reloj con su GPS; al salir genera una alerta ZONA_SEGURA con la severidad de la fila.
CREATE TABLE zona_segura (
	id_paciente uuid NOT NULL REFERENCES paciente (id_paciente) ON DELETE CASCADE,
	vigente_desde timestamptz NOT NULL,
	latitud varchar NOT NULL,                   -- cifrado
	longitud varchar NOT NULL,                  -- cifrado
	radio_metros int NOT NULL,
	severidad varchar NOT NULL DEFAULT 'MEDIA' CHECK (severidad IN ('LEVE', 'MEDIA', 'GRAVE')),
	PRIMARY KEY (id_paciente, vigente_desde),
	CONSTRAINT zona_segura_radio CHECK (radio_metros > 0)
);

-- Interruptores de los dos módulos. La zona segura exige el GPS; que exista una zona guardada lo valida
-- el backend, porque está en otra tabla. Las filas anteriores quedan con el botón apagado, porque entonces
-- no existía.
ALTER TABLE configuracion
	ADD COLUMN zona_segura_activa boolean NOT NULL DEFAULT false,
	ADD COLUMN boton_ayuda_activo boolean NOT NULL DEFAULT false,
	ADD CONSTRAINT configuracion_zona_gps CHECK (NOT zona_segura_activa OR gps_activo);
ALTER TABLE configuracion ALTER COLUMN boton_ayuda_activo SET DEFAULT true;

-- El botón queda activo para todos: igual que en V8, el historial solo agrega filas y cada versión nueva
-- queda en la auditoría sin usuario, porque la hizo el sistema.
WITH vigentes AS (
	SELECT DISTINCT ON (id_paciente) *
	FROM configuracion
	ORDER BY id_paciente, vigente_desde DESC
), nuevas AS (
	INSERT INTO configuracion (id_paciente, vigente_desde, caidas_activa, fc_activa, spo2_activa, inactividad_activa,
		tendencia_activa, gps_activo, modo_conservador, minutos_espera, zona_segura_activa, boton_ayuda_activo)
	SELECT id_paciente, now(), caidas_activa, fc_activa, spo2_activa, inactividad_activa,
		tendencia_activa, gps_activo, modo_conservador, minutos_espera, zona_segura_activa, true
	FROM vigentes
	RETURNING id_paciente, vigente_desde
)
INSERT INTO registro_auditoria (accion, entidad, id_entidad, detalle, fecha_hora)
SELECT 'MODIFICAR', 'CONFIGURACION',
	id_paciente || '|' || to_char(vigente_desde AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
	'{"motivo": "Botón de ayuda activado para todos"}'::jsonb, vigente_desde
FROM nuevas;

-- Tipos de evento nuevos. El botón de ayuda siempre es leve: respeta el horario hábil como cualquier leve.
ALTER TABLE alerta DROP CONSTRAINT alerta_tipo_evento_check;
ALTER TABLE alerta ADD CONSTRAINT alerta_tipo_evento_check
	CHECK (tipo_evento IN ('CAIDA', 'FC', 'SPO2', 'FC_SPO2', 'TENDENCIA', 'INACTIVIDAD', 'ZONA_SEGURA',
		'BOTON_AYUDA', 'SILENCIO_DATOS', 'BATERIA_BAJA', 'RELOJ_NO_PUESTO'));
ALTER TABLE alerta ADD CONSTRAINT alerta_boton_leve CHECK (tipo_evento <> 'BOTON_AYUDA' OR severidad = 'LEVE');

ALTER TABLE evento_descartado DROP CONSTRAINT evento_descartado_tipo_evento_check;
ALTER TABLE evento_descartado ADD CONSTRAINT evento_descartado_tipo_evento_check
	CHECK (tipo_evento IN ('CAIDA', 'FC', 'SPO2', 'FC_SPO2', 'TENDENCIA', 'INACTIVIDAD', 'ZONA_SEGURA',
		'BOTON_AYUDA', 'BATERIA_BAJA', 'RELOJ_NO_PUESTO'));

-- Los cambios de la zona quedan en la auditoría con la entidad propia.
ALTER TABLE registro_auditoria DROP CONSTRAINT registro_auditoria_entidad_check;
ALTER TABLE registro_auditoria ADD CONSTRAINT registro_auditoria_entidad_check
	CHECK (entidad IN ('PACIENTE', 'FICHA_PACIENTE', 'CONDICION_PACIENTE', 'CONTACTO', 'CONTACTO_PACIENTE',
		'CONFIGURACION', 'UMBRAL', 'ZONA_SEGURA', 'DOCUMENTO', 'SMARTWATCH', 'ASIGNACION_RELOJ', 'ALERTA',
		'ATENCION_ALERTA', 'SEGUIMIENTO', 'USUARIO', 'ROL', 'PERMISO_ROL', 'REPORTE'));

-- Aviso a la oficina PRLAC por WhatsApp: el teléfono de la cuenta pasa a ser su número de WhatsApp, y Meta
-- exige el opt-in, como en contacto. Que un coordinador tenga teléfono lo valida el backend al crear la
-- cuenta: una regla aquí rechazaría las cuentas que ya existen sin teléfono.
ALTER TABLE usuario
	ADD COLUMN fecha_opt_in timestamptz,
	ADD COLUMN fecha_revocacion_opt_in timestamptz,
	ADD CONSTRAINT usuario_opt_in_telefono CHECK (fecha_opt_in IS NULL OR telefono IS NOT NULL),
	ADD CONSTRAINT usuario_revocacion_opt_in CHECK (fecha_revocacion_opt_in IS NULL OR fecha_opt_in IS NOT NULL);
