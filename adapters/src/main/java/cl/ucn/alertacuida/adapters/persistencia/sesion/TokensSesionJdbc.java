package cl.ucn.alertacuida.adapters.persistencia.sesion;

import cl.ucn.alertacuida.application.sesion.modelo.TokenSesion;
import cl.ucn.alertacuida.application.sesion.puerto.TokensSesion;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tokens de renovación en token_sesion. El token mismo nunca llega a la base: solo su hash. */
@Repository
public class TokensSesionJdbc implements TokensSesion {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public TokensSesionJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(UUID idUsuario, String hashToken, Instant emision, Instant expiracion) {
		jdbc.sql("""
				INSERT INTO token_sesion (id_usuario, hash_token, fecha_emision, fecha_expiracion)
				VALUES (:usuario, :hash, :emision, :expiracion)""")
				.param("usuario", idUsuario)
				.param("hash", hashToken)
				.param("emision", Timestamp.from(emision))
				.param("expiracion", Timestamp.from(expiracion))
				.update();
	}

	@Override
	public Optional<TokenSesion> buscar(String hashToken) {
		return jdbc.sql("""
				SELECT id_token, id_usuario, fecha_emision, fecha_expiracion, fecha_revocacion
				FROM token_sesion WHERE hash_token = :hash""")
				.param("hash", hashToken)
				.query((fila, n) -> {
					Timestamp revocacion = fila.getTimestamp("fecha_revocacion");
					return new TokenSesion(fila.getObject("id_token", UUID.class),
							fila.getObject("id_usuario", UUID.class), fila.getTimestamp("fecha_emision").toInstant(),
							fila.getTimestamp("fecha_expiracion").toInstant(),
							revocacion == null ? null : revocacion.toInstant());
				})
				.optional();
	}

	/** La condición conserva la primera fecha: de ella depende la gracia de la rotación. */
	@Override
	public void revocar(UUID idToken, Instant fecha) {
		jdbc.sql("UPDATE token_sesion SET fecha_revocacion = :fecha WHERE id_token = :id AND fecha_revocacion IS NULL")
				.param("fecha", Timestamp.from(fecha))
				.param("id", idToken)
				.update();
	}

	@Override
	public void eliminarTodos(UUID idUsuario) {
		jdbc.sql("DELETE FROM token_sesion WHERE id_usuario = :usuario").param("usuario", idUsuario).update();
	}

	@Override
	public void eliminar(String hashToken) {
		jdbc.sql("DELETE FROM token_sesion WHERE hash_token = :hash").param("hash", hashToken).update();
	}

	@Override
	public void eliminarVencidos(Instant ahora) {
		jdbc.sql("DELETE FROM token_sesion WHERE fecha_expiracion <= :ahora")
				.param("ahora", Timestamp.from(ahora))
				.update();
	}

}
