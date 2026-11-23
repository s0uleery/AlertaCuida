-- Vigilante del escalamiento (2.10.6): si una alerta del reloj sigue abierta y el reloj no pide avisos en un plazo,
-- el servidor avisa por correo a los coordinadores, una vez por alerta.

-- Cuándo se pidió (el reloj) o se encoló (el servidor) cada aviso. Mide "sin noticias del reloj": la hora de envío
-- no sirve, porque un aviso puede salir minutos después por los reintentos. Las filas anteriores toman la primera
-- hora conocida de cada una.
ALTER TABLE registro_intento ADD COLUMN fecha_hora_solicitud timestamptz;
UPDATE registro_intento i
SET fecha_hora_solicitud = COALESCE(i.fecha_hora_proximo_envio, i.fecha_hora_envio, i.fecha_hora_fallo,
	(SELECT a.fecha_hora_recepcion FROM alerta a WHERE a.id_alerta = i.id_alerta));
ALTER TABLE registro_intento ALTER COLUMN fecha_hora_solicitud SET NOT NULL,
	ALTER COLUMN fecha_hora_solicitud SET DEFAULT now();

-- Para qué es el aviso: AVISO (el que pidió el reloj, el de recibe_avisos o el respaldo de un WhatsApp) o VIGILANTE
-- (la alerta no avanza). Solo los avisos al equipo pueden ser del vigilante. Entra en la unicidad de los avisos al
-- equipo: una cuenta puede recibir, de la misma alerta y por el mismo canal, un aviso y el del vigilante.
ALTER TABLE registro_intento
	ADD COLUMN proposito varchar NOT NULL DEFAULT 'AVISO' CHECK (proposito IN ('AVISO', 'VIGILANTE')),
	ADD CONSTRAINT intento_vigilante_interno CHECK (proposito = 'AVISO' OR id_contacto_interno IS NOT NULL),
	DROP CONSTRAINT registro_intento_interno_unico,
	ADD CONSTRAINT registro_intento_interno_unico UNIQUE (id_alerta, id_contacto_interno, canal, proposito);
