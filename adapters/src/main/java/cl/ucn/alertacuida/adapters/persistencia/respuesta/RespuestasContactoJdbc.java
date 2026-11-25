package cl.ucn.alertacuida.adapters.persistencia.respuesta;

import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.application.respuesta.puerto.RespuestasContacto;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Respuestas de los contactos en registro_intento (columnas respuesta y fecha_hora_respuesta). El estado de
 * la alerta sale de la vista alerta_estado, que ya cuenta un VOY_EN_CAMINO como confirmación.
 */
@Repository
public class RespuestasContactoJdbc implements RespuestasContacto {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public RespuestasContactoJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Solo los correos y WhatsApp que piden confirmar: los avisos a un vínculo de la lista del paciente y, en las
	 * alertas de salud, el aviso a la oficina y el de agravamiento a un coordinador (contacto interno). Los del
	 * vigilante y los de las técnicas no confirman.
	 */
	@Override
	public Optional<AvisoRespondible> buscar(UUID idIntento) {
		return jdbc.sql("""
				SELECT i.id_intento, i.id_alerta, a.numero, p.alias, a.tipo_evento, e.severidad_actual,
					coalesce(a.fecha_hora_deteccion, a.fecha_hora_recepcion) AS fecha_hora, e.estado_contacto,
					i.respuesta, i.fecha_hora_respuesta, i.fecha_hora_llegada, s.numero_sim,
					a.origen = 'RELOJ' AS del_reloj
				FROM registro_intento i
				JOIN alerta a ON a.id_alerta = i.id_alerta
				JOIN alerta_estado e ON e.id_alerta = i.id_alerta
				JOIN asignacion_reloj r ON r.id_asignacion = a.id_asignacion
				JOIN smartwatch s ON s.id_dispositivo = r.id_dispositivo
				JOIN paciente p ON p.id_paciente = r.id_paciente
				WHERE i.id_intento = :id AND i.canal IN ('CORREO', 'WHATSAPP') AND (i.id_contacto_paciente IS NOT NULL
					OR (i.id_contacto_interno IS NOT NULL AND i.proposito IN ('AVISO', 'AGRAVAMIENTO')
						AND a.tipo_evento NOT IN (:tecnicas)))""")
				.param("id", idIntento)
				.param("tecnicas", TipoEvento.tecnicas().stream().map(Enum::name).toList())
				.query((fila, n) -> {
					String respuesta = fila.getString("respuesta");
					Timestamp fechaRespuesta = fila.getTimestamp("fecha_hora_respuesta");
					Timestamp llegada = fila.getTimestamp("fecha_hora_llegada");
					return new AvisoRespondible(fila.getObject("id_intento", UUID.class),
							fila.getObject("id_alerta", UUID.class), fila.getLong("numero"), fila.getString("alias"),
							TipoEvento.valueOf(fila.getString("tipo_evento")),
							Severidad.valueOf(fila.getString("severidad_actual")),
							fila.getTimestamp("fecha_hora").toInstant(),
							EstadoContacto.valueOf(fila.getString("estado_contacto")),
							respuesta == null ? null : RespuestaContacto.valueOf(respuesta),
							fechaRespuesta == null ? null : fechaRespuesta.toInstant(),
							llegada == null ? null : llegada.toInstant(), fila.getString("numero_sim"),
							fila.getBoolean("del_reloj"));
				})
				.optional();
	}

	/** La condición {@code respuesta IS NULL} hace que, si dos respuestas llegan a la vez, valga la primera. */
	@Override
	public boolean registrar(UUID idIntento, RespuestaContacto respuesta, Instant fecha) {
		return jdbc.sql("""
				UPDATE registro_intento SET respuesta = :respuesta, fecha_hora_respuesta = :fecha
				WHERE id_intento = :id AND respuesta IS NULL""")
				.param("respuesta", respuesta.name())
				.param("fecha", Timestamp.from(fecha))
				.param("id", idIntento)
				.update() == 1;
	}

	/** Una fila sin fecha de envío; el envío a Firebase toma las pendientes. */
	@Override
	public void dejarAvisoConfirmadaPendiente(UUID idAlerta) {
		jdbc.sql("""
				INSERT INTO aviso_fcm (id_alerta, tipo) VALUES (:id, 'ALERTA_CONFIRMADA')
				ON CONFLICT (id_alerta, tipo) DO NOTHING""")
				.param("id", idAlerta)
				.update();
	}


	/** La condición {@code fecha_hora_llamada IS NULL} guarda solo la primera vez. */
	@Override
	public boolean registrarLlamada(UUID idIntento, Instant fecha) {
		return jdbc.sql("""
				UPDATE registro_intento SET fecha_hora_llamada = :fecha
				WHERE id_intento = :id AND fecha_hora_llamada IS NULL""")
				.param("fecha", Timestamp.from(fecha))
				.param("id", idIntento)
				.update() == 1;
	}

	/** Las condiciones repiten la regla intento_llegada de V17: solo quien dijo que iba, una vez. */
	@Override
	public boolean registrarLlegada(UUID idIntento, Instant fecha) {
		return jdbc.sql("""
				UPDATE registro_intento SET fecha_hora_llegada = :fecha
				WHERE id_intento = :id AND fecha_hora_llegada IS NULL AND respuesta = 'VOY_EN_CAMINO'""")
				.param("fecha", Timestamp.from(fecha))
				.param("id", idIntento)
				.update() == 1;
	}

	/**
	 * Cierra la atención sin resolutor, clasificación ni observaciones (un cierre del sistema); si alguien la
	 * había tomado, sigue como responsable. La condición sobre el cierre no toca una alerta ya cerrada.
	 */
	@Override
	public boolean resolverPorLlegada(UUID idAlerta, Instant fecha) {
		return jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, fecha_hora_cierre) VALUES (:id, :fecha)
				ON CONFLICT (id_alerta) DO UPDATE SET fecha_hora_cierre = EXCLUDED.fecha_hora_cierre
				WHERE atencion_alerta.fecha_hora_cierre IS NULL""")
				.param("id", idAlerta)
				.param("fecha", Timestamp.from(fecha))
				.update() == 1;
	}

	/** Una fila sin fecha de envío; el envío a Firebase toma las pendientes. */
	@Override
	public void dejarAvisoResueltaPendiente(UUID idAlerta) {
		jdbc.sql("""
				INSERT INTO aviso_fcm (id_alerta, tipo) VALUES (:id, 'ALERTA_RESUELTA')
				ON CONFLICT (id_alerta, tipo) DO NOTHING""")
				.param("id", idAlerta)
				.update();
	}

}
