-- Página del enlace del correo de alerta (decisiones del 24-11-2026):
-- "Llamar al paciente" llama al número de la SIM del reloj y queda registrado (solo la primera vez), y
-- "Llegué donde el paciente" lo marca quien ya respondió "Voy en camino": queda registrado y resuelve la alerta.
-- El coalesce hace que una llegada sin respuesta se rechace: sin él, la comparación con NULL daría NULL y el
-- CHECK la dejaría pasar.
ALTER TABLE registro_intento
	ADD COLUMN fecha_hora_llamada timestamptz,
	ADD COLUMN fecha_hora_llegada timestamptz,
	ADD CONSTRAINT intento_llegada CHECK (fecha_hora_llegada IS NULL
		OR coalesce(respuesta = 'VOY_EN_CAMINO' AND fecha_hora_llegada >= fecha_hora_respuesta, false));
