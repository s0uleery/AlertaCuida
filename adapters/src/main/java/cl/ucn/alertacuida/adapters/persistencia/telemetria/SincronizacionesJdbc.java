package cl.ucn.alertacuida.adapters.persistencia.telemetria;

import cl.ucn.alertacuida.application.telemetria.modelo.EstadoInformado;
import cl.ucn.alertacuida.application.telemetria.modelo.EventoDescartado;
import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRecibida;
import cl.ucn.alertacuida.application.telemetria.modelo.LoteRecibido;
import cl.ucn.alertacuida.application.telemetria.modelo.SincronizacionGuardada;
import cl.ucn.alertacuida.application.telemetria.modelo.UbicacionRecibida;
import cl.ucn.alertacuida.application.telemetria.modelo.VersionInformada;
import cl.ucn.alertacuida.application.telemetria.puerto.Sincronizaciones;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lotes del reloj en sincronizacion, registro_vital, ubicacion y evento_descartado.
 *
 * <p>Lo repetido lo descarta la base con {@code ON CONFLICT DO NOTHING}, sin error: un lote por
 * eventId, y una lectura por asignación, tipo y hora. Así, dos reintentos que llegan a la vez
 * tampoco duplican nada. Las lecturas se insertan en un solo viaje a la base (batch).
 */
@Repository
public class SincronizacionesJdbc implements Sincronizaciones {

	private final JdbcClient jdbc;
	private final NamedParameterJdbcTemplate lotes;

	/**
	 * @param jdbc  cliente de Spring sobre la conexión a PostgreSQL
	 * @param lotes para insertar muchas filas en un solo viaje
	 */
	public SincronizacionesJdbc(JdbcClient jdbc, NamedParameterJdbcTemplate lotes) {
		this.jdbc = jdbc;
		this.lotes = lotes;
	}

	@Override
	public Optional<SincronizacionGuardada> buscar(UUID eventId) {
		return jdbc.sql("""
				SELECT s.id_sincronizacion, s.id_asignacion, s.fecha_hora_recepcion,
					(SELECT count(*) FROM registro_vital r WHERE r.id_sincronizacion = s.id_sincronizacion) AS lecturas
				FROM sincronizacion s WHERE s.event_id = :eventId""")
				.param("eventId", eventId)
				.query((fila, n) -> new SincronizacionGuardada(fila.getObject("id_sincronizacion", UUID.class),
						fila.getObject("id_asignacion", UUID.class),
						fila.getTimestamp("fecha_hora_recepcion").toInstant(), fila.getInt("lecturas")))
				.optional();
	}

	@Override
	public Optional<UUID> crear(UUID idAsignacion, LoteRecibido lote, Instant recepcion) {
		EstadoInformado estado = lote.estado();
		return jdbc.sql("""
				INSERT INTO sincronizacion (event_id, id_asignacion, hash_configuracion, fecha_hora_envio,
					fecha_hora_recepcion, nivel_bateria, reloj_puesto, minutos_puesto, tipo_conexion, version_app)
				VALUES (:eventId, :asignacion, :configuracion, :envio, :recepcion, :bateria, :puesto, :minutos,
					:conexion, :version)
				ON CONFLICT (event_id) DO NOTHING
				RETURNING id_sincronizacion""")
				.param("eventId", lote.eventId())
				.param("asignacion", idAsignacion)
				.param("configuracion", estado.versionConfiguracion())
				.param("envio", Timestamp.from(lote.envio()))
				.param("recepcion", Timestamp.from(recepcion))
				.param("bateria", estado.nivelBateria())
				.param("puesto", estado.relojPuesto())
				.param("minutos", estado.minutosPuesto())
				.param("conexion", estado.conexion().name())
				.param("version", estado.versionApp())
				.query(UUID.class)
				.optional();
	}

	@Override
	public int guardarLecturas(UUID idSincronizacion, UUID idAsignacion, List<LecturaRecibida> lecturas) {
		if (lecturas.isEmpty()) {
			return 0;
		}
		SqlParameterSource[] filas = lecturas.stream()
				.map(l -> new MapSqlParameterSource()
						.addValue("sincronizacion", idSincronizacion)
						.addValue("asignacion", idAsignacion)
						.addValue("tipo", l.tipo().name())
						.addValue("valor", l.valor())
						.addValue("medicion", Timestamp.from(l.medicion())))
				.toArray(SqlParameterSource[]::new);
		int[] insertadas = lotes.batchUpdate("""
				INSERT INTO registro_vital (id_sincronizacion, id_asignacion, tipo, valor, fecha_hora_medicion)
				VALUES (:sincronizacion, :asignacion, :tipo, :valor, :medicion)
				ON CONFLICT (id_asignacion, tipo, fecha_hora_medicion) DO NOTHING""", filas);
		// Cada fila responde 1 si se insertó y 0 si ya estaba
		return Arrays.stream(insertadas).map(n -> n > 0 ? 1 : 0).sum();
	}

	@Override
	public void guardarUbicaciones(UUID idSincronizacion, UUID idAsignacion, List<UbicacionRecibida> ubicaciones) {
		if (ubicaciones.isEmpty()) {
			return;
		}
		SqlParameterSource[] filas = ubicaciones.stream()
				.map(u -> new MapSqlParameterSource()
						.addValue("sincronizacion", idSincronizacion)
						.addValue("asignacion", idAsignacion)
						.addValue("latitud", u.latitud())
						.addValue("longitud", u.longitud())
						.addValue("precision", u.precisionMetros())
						.addValue("medicion", Timestamp.from(u.medicion())))
				.toArray(SqlParameterSource[]::new);
		lotes.batchUpdate("""
				INSERT INTO ubicacion (id_sincronizacion, id_asignacion, latitud, longitud, precision_metros,
					fecha_hora_medicion)
				VALUES (:sincronizacion, :asignacion, :latitud, :longitud, :precision, :medicion)
				ON CONFLICT (id_asignacion, fecha_hora_medicion) DO NOTHING""", filas);
	}

	@Override
	public void guardarDescartados(UUID idSincronizacion, List<EventoDescartado> descartados) {
		for (EventoDescartado descartado : descartados) {
			jdbc.sql("""
					INSERT INTO evento_descartado (id_sincronizacion, tipo_evento, cantidad)
					VALUES (:sincronizacion, :tipo, :cantidad)""")
					.param("sincronizacion", idSincronizacion)
					.param("tipo", descartado.tipo().name())
					.param("cantidad", descartado.cantidad())
					.update();
		}
	}

	/**
	 * El último lote da la versión; "desde" es el primer lote posterior al último que informó otra
	 * versión (o ninguna). Si el reloj pasó de A a B y volvió a A, cuenta desde que volvió a A.
	 */
	@Override
	public Optional<VersionInformada> versionInformada(UUID idAsignacion) {
		return jdbc.sql("""
				SELECT u.hash_configuracion, u.fecha_hora_recepcion AS ultima,
					(SELECT min(s.fecha_hora_recepcion) FROM sincronizacion s
					WHERE s.id_asignacion = u.id_asignacion
						AND s.hash_configuracion IS NOT DISTINCT FROM u.hash_configuracion
						AND s.fecha_hora_recepcion > COALESCE(
							(SELECT max(o.fecha_hora_recepcion) FROM sincronizacion o
							WHERE o.id_asignacion = u.id_asignacion
								AND o.hash_configuracion IS DISTINCT FROM u.hash_configuracion),
							'-infinity')) AS desde
				FROM sincronizacion u
				WHERE u.id_asignacion = :asignacion
				ORDER BY u.fecha_hora_recepcion DESC
				LIMIT 1""")
				.param("asignacion", idAsignacion)
				.query((fila, n) -> new VersionInformada(fila.getString("hash_configuracion"),
						fila.getTimestamp("ultima").toInstant(), fila.getTimestamp("desde").toInstant()))
				.optional();
	}

}
