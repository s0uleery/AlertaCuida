-- Correo institucional: contacto interno para las cuentas del equipo, y reintentos de los envíos.

-- Contacto interno: una cuenta del equipo PRLAC que recibe avisos del servidor (el correo de recibe_avisos, el
-- WhatsApp a la oficina y su correo de respaldo). Es solo un enlace a usuario: nombre, correo, teléfono y opt-in
-- se leen de la cuenta, sin copiarlos. No entra en la lista de ningún paciente, así que el reloj no lo recorre.
-- Contacto externo: la persona de la lista de un paciente, como hasta ahora.
ALTER TABLE contacto
	ADD COLUMN id_usuario uuid UNIQUE REFERENCES usuario (id_usuario),
	ALTER COLUMN nombre DROP NOT NULL,
	DROP CONSTRAINT contacto_canal;
ALTER TABLE contacto
	-- El externo necesita nombre y algún canal propio
	ADD CONSTRAINT contacto_canal CHECK (id_usuario IS NOT NULL OR whatsapp IS NOT NULL OR correo IS NOT NULL),
	ADD CONSTRAINT contacto_nombre CHECK (id_usuario IS NOT NULL OR nombre IS NOT NULL),
	-- El interno no guarda datos propios (los hash y la revocación quedan vacíos por las otras reglas)
	ADD CONSTRAINT contacto_interno_sin_datos CHECK (id_usuario IS NULL
		OR (nombre IS NULL AND whatsapp IS NULL AND correo IS NULL AND fecha_opt_in IS NULL));

-- Un contacto no cambia de interno a externo ni al revés, y solo los externos entran en la lista de un paciente.
-- Responde como un CHECK (código 23514).
CREATE FUNCTION contacto_tipo_fijo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
	IF NEW.id_usuario IS DISTINCT FROM OLD.id_usuario THEN
		RAISE EXCEPTION 'Un contacto no puede cambiar de interno a externo ni al revés'
			USING ERRCODE = 'check_violation';
	END IF;
	RETURN NEW;
END $$;

CREATE TRIGGER contacto_tipo_fijo BEFORE UPDATE OF id_usuario ON contacto
	FOR EACH ROW EXECUTE FUNCTION contacto_tipo_fijo();

CREATE FUNCTION contacto_paciente_externo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
	IF EXISTS (SELECT 1 FROM contacto WHERE id_contacto = NEW.id_contacto AND id_usuario IS NOT NULL) THEN
		RAISE EXCEPTION 'Un contacto interno no entra en la lista de un paciente'
			USING ERRCODE = 'check_violation';
	END IF;
	RETURN NEW;
END $$;

CREATE TRIGGER contacto_paciente_externo BEFORE INSERT OR UPDATE OF id_contacto ON contacto_paciente
	FOR EACH ROW EXECUTE FUNCTION contacto_paciente_externo();

-- Cada aviso va a un vínculo de la lista del paciente (pedido por el reloj, con su ronda) o a un contacto
-- interno (decidido por el servidor, sin ronda), nunca a los dos.
-- Bandeja de salida: fecha_hora_proximo_envio indica que el envío está pendiente y desde cuándo puede salir.
-- Si falla, se reprograma y suma un reintento; agotados (1, 5 y 15 minutos, en application.properties),
-- queda con fecha_hora_fallo y motivo_fallo, a la vista del equipo.
ALTER TABLE registro_intento
	ALTER COLUMN id_contacto_paciente DROP NOT NULL,
	ALTER COLUMN ronda DROP NOT NULL,
	ADD COLUMN id_contacto_interno uuid REFERENCES contacto (id_contacto),
	ADD COLUMN reintentos int NOT NULL DEFAULT 0,
	ADD COLUMN fecha_hora_proximo_envio timestamptz,
	ADD CONSTRAINT intento_destino CHECK ((id_contacto_paciente IS NULL) <> (id_contacto_interno IS NULL)),
	ADD CONSTRAINT intento_ronda_externo CHECK ((ronda IS NULL) = (id_contacto_paciente IS NULL)),
	ADD CONSTRAINT intento_reintentos CHECK (reintentos >= 0),
	-- Solo un contacto del paciente confirma la alerta ("Voy en camino"): el estado del reloj depende de eso
	ADD CONSTRAINT intento_respuesta_externo CHECK (respuesta IS NULL OR id_contacto_paciente IS NOT NULL),
	ADD CONSTRAINT intento_pendiente CHECK (fecha_hora_proximo_envio IS NULL
		OR (fecha_hora_envio IS NULL AND fecha_hora_fallo IS NULL)),
	-- Un aviso por cuenta, canal y alerta, aunque el servidor lo encole varias veces
	ADD CONSTRAINT registro_intento_interno_unico UNIQUE (id_alerta, id_contacto_interno, canal);

-- Lo que la tarea programada busca para enviar
CREATE INDEX registro_intento_pendiente ON registro_intento (fecha_hora_proximo_envio)
	WHERE fecha_hora_proximo_envio IS NOT NULL;

CREATE FUNCTION intento_contacto_interno() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
	IF NEW.id_contacto_interno IS NOT NULL AND NOT EXISTS
		(SELECT 1 FROM contacto WHERE id_contacto = NEW.id_contacto_interno AND id_usuario IS NOT NULL) THEN
		RAISE EXCEPTION 'id_contacto_interno debe ser un contacto interno' USING ERRCODE = 'check_violation';
	END IF;
	RETURN NEW;
END $$;

CREATE TRIGGER intento_contacto_interno BEFORE INSERT OR UPDATE OF id_contacto_interno ON registro_intento
	FOR EACH ROW EXECUTE FUNCTION intento_contacto_interno();

-- Los avisos al equipo no son el recorrido de contactos del reloj: no cuentan para AVISANDO_CONTACTO
CREATE OR REPLACE VIEW alerta_estado AS
SELECT a.id_alerta,
	CASE
		WHEN a.fecha_hora_cancelacion IS NOT NULL THEN 'CANCELADA'
		WHEN t.fecha_hora_cierre IS NOT NULL THEN 'RESUELTA'
		WHEN EXISTS (SELECT 1 FROM registro_intento i
			WHERE i.id_alerta = a.id_alerta AND i.respuesta = 'VOY_EN_CAMINO') THEN 'CONFIRMADA'
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
