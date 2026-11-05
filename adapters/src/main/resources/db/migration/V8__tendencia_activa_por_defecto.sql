-- La regla de 2 lecturas quedó solo para los valores graves: sin el módulo de tendencia, la zona entre el
-- rango normal y el límite grave no genera alertas. Por eso el módulo pasa a estar activo para todos.
ALTER TABLE configuracion ALTER COLUMN tendencia_activa SET DEFAULT true;

-- El historial solo agrega filas: quienes lo tienen apagado reciben una versión nueva, igual a la vigente pero
-- con el módulo activo, y su registro de auditoría sin usuario, porque lo hizo el sistema. El motivo va vacío:
-- el de la versión anterior explicaba otro cambio. Quien no tiene configuración ya queda cubierto por el valor
-- por defecto.
WITH vigentes AS (
	SELECT DISTINCT ON (id_paciente) *
	FROM configuracion
	ORDER BY id_paciente, vigente_desde DESC
), nuevas AS (
	INSERT INTO configuracion (id_paciente, vigente_desde, caidas_activa, fc_activa, spo2_activa, inactividad_activa,
		tendencia_activa, gps_activo, modo_conservador, minutos_espera)
	SELECT id_paciente, now(), caidas_activa, fc_activa, spo2_activa, inactividad_activa,
		true, gps_activo, modo_conservador, minutos_espera
	FROM vigentes
	WHERE NOT tendencia_activa
	RETURNING id_paciente, vigente_desde
)
INSERT INTO registro_auditoria (accion, entidad, id_entidad, detalle, fecha_hora)
SELECT 'MODIFICAR', 'CONFIGURACION',
	id_paciente || '|' || to_char(vigente_desde AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
	'{"motivo": "Módulo de tendencia activado para todos"}'::jsonb, vigente_desde
FROM nuevas;
