package cl.ucn.alertacuida.adapters.persistencia.escalamiento;

import cl.ucn.alertacuida.application.escalamiento.modelo.AlertaTrabada;
import cl.ucn.alertacuida.application.escalamiento.puerto.EscalamientosTrabados;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Alertas trabadas y aviso del vigilante, sobre alerta, la vista alerta_estado y registro_intento. La última
 * noticia del reloj es la más reciente entre la recepción de la alerta, su agravamiento y la solicitud de su
 * último aviso a un contacto (registro_intento.fecha_hora_solicitud). Un correo de respaldo cuenta como
 * noticia, pero sale casi a la vez que el aviso que respalda.
 */
@Repository
public class EscalamientosTrabadosJdbc implements EscalamientosTrabados {

	/** Rol que recibe los avisos de la oficina PRLAC. */
	static final String ROL_OFICINA = "COORDINADOR";

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public EscalamientosTrabadosJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Solo estados RECIBIDA y AVISANDO_CONTACTO: los demás terminaron o el reloj ya avisó que terminó. */
	@Override
	public List<AlertaTrabada> trabadas(Instant limite) {
		return jdbc.sql("""
				SELECT id_alerta, ultima FROM (
					SELECT a.id_alerta, GREATEST(a.fecha_hora_recepcion, a.fecha_hora_agravamiento,
						(SELECT max(i.fecha_hora_solicitud) FROM registro_intento i
							WHERE i.id_alerta = a.id_alerta AND i.id_contacto_paciente IS NOT NULL)) AS ultima
					FROM alerta a
					JOIN alerta_estado e ON e.id_alerta = a.id_alerta
					WHERE a.origen = 'RELOJ' AND NOT a.es_simulacro
					AND e.estado_contacto IN ('RECIBIDA', 'AVISANDO_CONTACTO')
					AND NOT EXISTS (SELECT 1 FROM registro_intento v
						WHERE v.id_alerta = a.id_alerta AND v.proposito = 'VIGILANTE')
				) t
				WHERE ultima <= :limite""")
				.param("limite", Timestamp.from(limite))
				.query((fila, n) -> new AlertaTrabada(fila.getObject("id_alerta", UUID.class),
						fila.getTimestamp("ultima").toInstant()))
				.list();
	}

	/** Los coordinadores reciben el aviso como contacto interno, igual que los correos de recibe_avisos. */
	@Override
	public void avisarCoordinadores(UUID idAlerta, Instant ahora) {
		jdbc.sql("""
				INSERT INTO contacto (id_usuario)
				SELECT id_usuario FROM usuario WHERE activo AND codigo_rol = :rol
				ON CONFLICT (id_usuario) DO NOTHING""")
				.param("rol", ROL_OFICINA)
				.update();
		jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_interno, canal, proposito,
					fecha_hora_solicitud, fecha_hora_proximo_envio)
				SELECT gen_random_uuid(), :alerta, c.id_contacto, 'CORREO', 'VIGILANTE', :ahora, :ahora
				FROM contacto c JOIN usuario u ON u.id_usuario = c.id_usuario
				WHERE u.activo AND u.codigo_rol = :rol
				ON CONFLICT DO NOTHING""")
				.param("alerta", idAlerta)
				.param("ahora", Timestamp.from(ahora))
				.param("rol", ROL_OFICINA)
				.update();
	}

}
