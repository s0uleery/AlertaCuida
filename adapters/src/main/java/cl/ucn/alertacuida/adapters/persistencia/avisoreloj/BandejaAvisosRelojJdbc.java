package cl.ucn.alertacuida.adapters.persistencia.avisoreloj;

import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoPendiente;
import cl.ucn.alertacuida.application.avisoreloj.modelo.TipoAvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.BandejaAvisosReloj;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Avisos al reloj en la tabla aviso_fcm. fecha_hora_envio guarda el último envío, que es desde donde se
 * cuenta la espera del siguiente reintento, e intentos cuenta los envíos hechos.
 */
@Repository
public class BandejaAvisosRelojJdbc implements BandejaAvisosReloj {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public BandejaAvisosRelojJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Quien confirmó es el primero que respondió "Voy en camino", como en el estado de la alerta. */
	@Override
	public List<AvisoPendiente> sinAcusar(int maximoEnvios) {
		return jdbc.sql("""
				SELECT f.id_aviso, s.id_dispositivo, s.token_fcm, f.tipo, a.event_id, c.id_contacto_paciente,
					f.intentos, f.fecha_hora_envio
				FROM aviso_fcm f
				JOIN alerta a USING (id_alerta)
				JOIN asignacion_reloj v USING (id_asignacion)
				JOIN smartwatch s USING (id_dispositivo)
				LEFT JOIN LATERAL (
					SELECT i.id_contacto_paciente FROM registro_intento i
					WHERE i.id_alerta = a.id_alerta AND i.respuesta = 'VOY_EN_CAMINO'
					ORDER BY i.fecha_hora_respuesta LIMIT 1) c ON f.tipo = 'ALERTA_CONFIRMADA'
				WHERE f.fecha_hora_recepcion IS NULL AND s.token_fcm IS NOT NULL AND f.intentos < :maximo
				ORDER BY f.fecha_hora_envio NULLS FIRST, f.id_aviso""")
				.param("maximo", maximoEnvios)
				.query((fila, n) -> {
					Timestamp envio = fila.getTimestamp("fecha_hora_envio");
					return new AvisoPendiente(fila.getObject("id_aviso", UUID.class),
							fila.getObject("id_dispositivo", UUID.class), fila.getString("token_fcm"),
							TipoAvisoReloj.valueOf(fila.getString("tipo")), fila.getObject("event_id", UUID.class),
							fila.getObject("id_contacto_paciente", UUID.class), fila.getInt("intentos"),
							envio == null ? null : envio.toInstant());
				})
				.list();
	}

	@Override
	public void marcarEnviado(UUID idAviso, Instant ahora) {
		jdbc.sql("""
				UPDATE aviso_fcm SET fecha_hora_envio = :ahora, intentos = intentos + 1
				WHERE id_aviso = :id AND fecha_hora_recepcion IS NULL""")
				.param("ahora", Timestamp.from(ahora))
				.param("id", idAviso)
				.update();
	}

	/** La condición sobre el token evita borrar uno nuevo que el reloj haya registrado mientras tanto. */
	@Override
	public void olvidarToken(UUID idDispositivo, String tokenRechazado) {
		jdbc.sql("UPDATE smartwatch SET token_fcm = NULL WHERE id_dispositivo = :id AND token_fcm = :token")
				.param("id", idDispositivo)
				.param("token", tokenRechazado)
				.update();
	}

	@Override
	public boolean esDelVinculo(UUID idAviso, UUID idAsignacion) {
		return jdbc.sql("""
				SELECT count(*) FROM aviso_fcm f JOIN alerta a USING (id_alerta)
				WHERE f.id_aviso = :id AND a.id_asignacion = :asignacion""")
				.param("id", idAviso)
				.param("asignacion", idAsignacion)
				.query(Integer.class)
				.single() > 0;
	}

	/** La condición {@code fecha_hora_recepcion IS NULL} hace que repetirlo no cambie la hora. */
	@Override
	public void registrarRecepcion(UUID idAviso, Instant fecha) {
		jdbc.sql("""
				UPDATE aviso_fcm SET fecha_hora_recepcion = :fecha
				WHERE id_aviso = :id AND fecha_hora_recepcion IS NULL""")
				.param("fecha", Timestamp.from(fecha))
				.param("id", idAviso)
				.update();
	}

	/** Vigente = el vínculo sin fecha de término. */
	@Override
	public Optional<String> tokenDelPaciente(UUID idPaciente) {
		return jdbc.sql("""
				SELECT s.token_fcm FROM asignacion_reloj v JOIN smartwatch s USING (id_dispositivo)
				WHERE v.id_paciente = :paciente AND v.fecha_fin IS NULL AND s.token_fcm IS NOT NULL""")
				.param("paciente", idPaciente)
				.query(String.class)
				.optional();
	}

}
