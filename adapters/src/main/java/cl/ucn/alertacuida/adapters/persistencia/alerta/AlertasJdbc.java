package cl.ucn.alertacuida.adapters.persistencia.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.ConfirmacionContacto;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Alertas en la tabla alerta. El estado no se guarda: se lee de la vista alerta_estado.
 *
 * <p>Una alerta por eventId: si dos reintentos llegan a la vez, {@code ON CONFLICT DO NOTHING}
 * guarda solo el primero.
 */
@Repository
public class AlertasJdbc implements Alertas {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public AlertasJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<AlertaGuardada> buscar(UUID eventId) {
		return jdbc.sql("""
				SELECT id_alerta, id_asignacion, numero, fecha_hora_cancelacion IS NOT NULL AS cancelada
				FROM alerta WHERE event_id = :eventId""")
				.param("eventId", eventId)
				.query((fila, n) -> guardada(fila))
				.optional();
	}

	/** Las alertas del reloj llevan origen RELOJ y la configuración con que se evaluaron (regla de V5). */
	@Override
	public Optional<AlertaGuardada> crear(UUID idAsignacion, AlertaRecibida alerta, Instant recepcion) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					evidencia, espera_dia_habil, fecha_hora_deteccion, fecha_hora_envio, fecha_hora_recepcion,
					fecha_hora_cancelacion)
				VALUES (:eventId, :asignacion, :configuracion, 'RELOJ', :tipo, :severidad, CAST(:evidencia AS jsonb),
					:espera, :deteccion, :envio, :recepcion, :cancelacion)
				ON CONFLICT (event_id) DO NOTHING
				RETURNING id_alerta, id_asignacion, numero, fecha_hora_cancelacion IS NOT NULL AS cancelada""")
				.param("eventId", alerta.eventId())
				.param("asignacion", idAsignacion)
				.param("configuracion", alerta.versionConfiguracion())
				.param("tipo", alerta.tipo().name())
				.param("severidad", alerta.severidad().name())
				.param("evidencia", alerta.evidencia())
				.param("espera", alerta.esperaDiaHabil())
				.param("deteccion", marca(alerta.deteccion()))
				.param("envio", marca(alerta.envio()))
				.param("recepcion", marca(recepcion))
				.param("cancelacion", marca(alerta.cancelacion()))
				.query((fila, n) -> guardada(fila))
				.optional();
	}

	/** El contacto que confirmó es el primero que respondió "Voy en camino". */
	@Override
	public EstadoAlerta estado(UUID idAlerta) {
		return jdbc.sql("""
				SELECT a.event_id, e.estado_contacto, e.severidad_actual, c.id_contacto_paciente,
					c.fecha_hora_respuesta
				FROM alerta a
				JOIN alerta_estado e USING (id_alerta)
				LEFT JOIN LATERAL (
					SELECT i.id_contacto_paciente, i.fecha_hora_respuesta FROM registro_intento i
					WHERE i.id_alerta = a.id_alerta AND i.respuesta = 'VOY_EN_CAMINO'
					ORDER BY i.fecha_hora_respuesta LIMIT 1) c ON true
				WHERE a.id_alerta = :id""")
				.param("id", idAlerta)
				.query((fila, n) -> {
					UUID contacto = fila.getObject("id_contacto_paciente", UUID.class);
					ConfirmacionContacto confirmacion = contacto == null ? null
							: new ConfirmacionContacto(contacto, fila.getTimestamp("fecha_hora_respuesta").toInstant());
					return EstadoAlerta.de(fila.getObject("event_id", UUID.class),
							EstadoContacto.valueOf(fila.getString("estado_contacto")),
							Severidad.valueOf(fila.getString("severidad_actual")), confirmacion);
				})
				.single();
	}

	/** Arma una {@link AlertaGuardada} desde una fila con id_alerta, id_asignacion, numero y cancelada. */
	private static AlertaGuardada guardada(ResultSet fila) throws SQLException {
		return new AlertaGuardada(fila.getObject("id_alerta", UUID.class), fila.getObject("id_asignacion", UUID.class),
				fila.getLong("numero"), fila.getBoolean("cancelada"));
	}

	/** @return el instante como Timestamp para JDBC, o null */
	private static Timestamp marca(Instant instante) {
		return instante == null ? null : Timestamp.from(instante);
	}

}
