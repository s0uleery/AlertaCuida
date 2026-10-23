package cl.ucn.alertacuida.adapters.persistencia.sesion;

import cl.ucn.alertacuida.application.sesion.puerto.RegistroAccesos;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Los accesos van en registro_auditoria (nota de USUARIO en docs/modelo-datos.dbml): no hay una
 * columna de "último acceso" ni un contador de fallos, se calculan desde aquí.
 */
@Repository
public class RegistroAccesosJdbc implements RegistroAccesos {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public RegistroAccesosJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void registrarLogin(UUID idUsuario, String ip, Instant fechaHora) {
		registrar(idUsuario, "LOGIN", ip, fechaHora);
	}

	@Override
	public void registrarLoginFallido(UUID idUsuario, String ip, Instant fechaHora) {
		registrar(idUsuario, "LOGIN_FALLIDO", ip, fechaHora);
	}

	/** Usa el índice registro_auditoria_usuario (id_usuario, accion, fecha_hora). */
	@Override
	public long contarFallidosRecientes(UUID idUsuario, Instant desde) {
		return jdbc.sql("""
				SELECT count(*) FROM registro_auditoria
				WHERE id_usuario = :id AND accion = 'LOGIN_FALLIDO' AND fecha_hora >= :desde
				AND fecha_hora > coalesce((SELECT max(fecha_hora) FROM registro_auditoria
					WHERE id_usuario = :id AND accion = 'LOGIN'), '-infinity')""")
				.param("id", idUsuario)
				.param("desde", Timestamp.from(desde))
				.query(Long.class)
				.single();
	}

	private void registrar(UUID idUsuario, String accion, String ip, Instant fechaHora) {
		jdbc.sql("""
				INSERT INTO registro_auditoria (id_usuario, accion, fecha_hora, ip)
				VALUES (:id, :accion, :fechaHora, :ip)""")
				.param("id", idUsuario)
				.param("accion", accion)
				.param("fechaHora", Timestamp.from(fechaHora))
				.param("ip", ip)
				.update();
	}

}
