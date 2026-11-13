package cl.ucn.alertacuida.adapters.persistencia.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.ConfirmacionContacto;
import cl.ucn.alertacuida.application.alerta.modelo.Discrepancia;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Alertas en las tablas alerta y discrepancia_alerta. El estado no se guarda: se lee de la vista
 * alerta_estado.
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
				SELECT id_alerta, id_asignacion, numero, tipo_evento, severidad,
					fecha_hora_cancelacion IS NOT NULL AS cancelada, fecha_hora_agravamiento IS NOT NULL AS agravada
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
				RETURNING id_alerta, id_asignacion, numero, tipo_evento, severidad,
					fecha_hora_cancelacion IS NOT NULL AS cancelada, fecha_hora_agravamiento IS NOT NULL AS agravada""")
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

	/** Una fila por discrepancia; sin discrepancias no hace nada. */
	@Override
	public void guardarDiscrepancias(UUID idAlerta, List<Discrepancia> discrepancias) {
		for (Discrepancia discrepancia : discrepancias) {
			jdbc.sql("INSERT INTO discrepancia_alerta (id_alerta, codigo, detalle) VALUES (:alerta, :codigo, :detalle)")
					.param("alerta", idAlerta)
					.param("codigo", discrepancia.codigo().name())
					.param("detalle", discrepancia.detalle())
					.update();
		}
	}

	/** Usa el índice (id_asignacion, fecha_hora_recepcion) de V5. */
	@Override
	public int contarRecientes(UUID idAsignacion, Instant desde) {
		return jdbc.sql("""
				SELECT count(*) FROM alerta
				WHERE id_asignacion = :asignacion AND origen = 'RELOJ' AND fecha_hora_cancelacion IS NULL
					AND fecha_hora_recepcion > :desde""")
				.param("asignacion", idAsignacion)
				.param("desde", marca(desde))
				.query(Integer.class)
				.single();
	}

	/**
	 * El candado (pg_advisory_xact_lock por vínculo y tipo, nota de ALERTA en el modelo) hace esperar a
	 * una segunda llamada simultánea hasta que la primera termine su transacción; así ve la alerta ya
	 * creada y no crea otra. "Abierta" = sin cierre en atencion_alerta; un simulacro no cuenta.
	 */
	@Override
	public boolean crearTecnicaSiNoHayAbierta(UUID idAsignacion, TipoEvento tipo, Severidad severidad,
			String detalle, boolean esperaDiaHabil, Instant ahora) {
		jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:clave, 0))")
				.param("clave", idAsignacion + ":" + tipo.name())
				.query((fila, n) -> 1)
				.single();
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, origen, tipo_evento, severidad, evidencia,
					espera_dia_habil, fecha_hora_deteccion, fecha_hora_recepcion)
				SELECT gen_random_uuid(), :asignacion, 'SERVIDOR', :tipo, :severidad, CAST(:detalle AS jsonb),
					:espera, :ahora, :ahora
				WHERE NOT EXISTS (
					SELECT 1 FROM alerta a LEFT JOIN atencion_alerta t USING (id_alerta)
					WHERE a.id_asignacion = :asignacion AND a.tipo_evento = :tipo AND NOT a.es_simulacro
						AND t.fecha_hora_cierre IS NULL)""")
				.param("asignacion", idAsignacion)
				.param("tipo", tipo.name())
				.param("severidad", severidad.name())
				.param("detalle", detalle)
				.param("espera", esperaDiaHabil)
				.param("ahora", marca(ahora))
				.update() == 1;
	}

	/** Cerrada por una persona = tiene cierre y resolutor; el sistema cierra sin resolutor. */
	@Override
	public Optional<Instant> ultimaCerradaPorPersona(UUID idAsignacion, TipoEvento tipo) {
		return jdbc.sql("""
				SELECT a.fecha_hora_recepcion, t.fecha_hora_cierre, t.id_usuario_resolutor
				FROM alerta a LEFT JOIN atencion_alerta t USING (id_alerta)
				WHERE a.id_asignacion = :asignacion AND a.tipo_evento = :tipo AND a.origen = 'SERVIDOR'
					AND NOT a.es_simulacro
				ORDER BY a.fecha_hora_recepcion DESC LIMIT 1""")
				.param("asignacion", idAsignacion)
				.param("tipo", tipo.name())
				.query((fila, n) -> fila.getTimestamp("fecha_hora_cierre") != null
						&& fila.getObject("id_usuario_resolutor") != null
								? Optional.of(fila.getTimestamp("fecha_hora_recepcion").toInstant())
								: Optional.<Instant>empty())
				.optional()
				.flatMap(cerrada -> cerrada);
	}

	/**
	 * Si alguien ya la tomó existe su fila en atencion_alerta: se completa el cierre y se conserva quién
	 * la tomó. Sin resolutor, la alerta queda como cerrada por el sistema.
	 */
	@Override
	public boolean cerrarTecnicaAbierta(UUID idAsignacion, TipoEvento tipo, Instant ahora) {
		return jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, fecha_hora_cierre)
				SELECT a.id_alerta, :ahora FROM alerta a LEFT JOIN atencion_alerta t USING (id_alerta)
				WHERE a.id_asignacion = :asignacion AND a.tipo_evento = :tipo AND a.origen = 'SERVIDOR'
					AND NOT a.es_simulacro AND t.fecha_hora_cierre IS NULL
				ON CONFLICT (id_alerta) DO UPDATE SET fecha_hora_cierre = EXCLUDED.fecha_hora_cierre
				WHERE atencion_alerta.fecha_hora_cierre IS NULL""")
				.param("asignacion", idAsignacion)
				.param("tipo", tipo.name())
				.param("ahora", marca(ahora))
				.update() > 0;
	}

	/** La condición {@code fecha_hora_agravamiento IS NULL} hace que repetirlo no cambie la hora. */
	@Override
	public boolean agravar(UUID idAlerta, Instant fecha, Integer segundosInmovilidad) {
		return jdbc.sql("""
				UPDATE alerta SET fecha_hora_agravamiento = :fecha, segundos_inmovilidad_agravamiento = :segundos
				WHERE id_alerta = :id AND fecha_hora_agravamiento IS NULL""")
				.param("fecha", marca(fecha))
				.param("segundos", segundosInmovilidad)
				.param("id", idAlerta)
				.update() == 1;
	}

	/** La condición {@code fecha_hora_sin_respuesta IS NULL} hace que repetirlo no cambie la hora. */
	@Override
	public void registrarSinRespuesta(UUID idAlerta, Instant fecha) {
		jdbc.sql("""
				UPDATE alerta SET fecha_hora_sin_respuesta = :fecha
				WHERE id_alerta = :id AND fecha_hora_sin_respuesta IS NULL""")
				.param("fecha", marca(fecha))
				.param("id", idAlerta)
				.update();
	}

	/** Arma una {@link AlertaGuardada} desde una fila de buscar o crear. */
	private static AlertaGuardada guardada(ResultSet fila) throws SQLException {
		return new AlertaGuardada(fila.getObject("id_alerta", UUID.class), fila.getObject("id_asignacion", UUID.class),
				fila.getLong("numero"), TipoEvento.valueOf(fila.getString("tipo_evento")),
				Severidad.valueOf(fila.getString("severidad")), fila.getBoolean("cancelada"),
				fila.getBoolean("agravada"));
	}

	/** @return el instante como Timestamp para JDBC, o null */
	private static Timestamp marca(Instant instante) {
		return instante == null ? null : Timestamp.from(instante);
	}

}
