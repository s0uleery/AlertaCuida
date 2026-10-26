package cl.ucn.alertacuida.adapters.persistencia.personal;

import cl.ucn.alertacuida.application.personal.modelo.TokenActivacion;
import cl.ucn.alertacuida.application.personal.puerto.TokensActivacion;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Enlaces de activación en token_accion (tipo ACTIVACION). La misma tabla guardará los de
 * recuperación de contraseña: por eso cada consulta filtra por tipo.
 */
@Repository
public class TokensActivacionJdbc implements TokensActivacion {

	private static final String TIPO = "ACTIVACION";

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public TokensActivacionJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** @param hashToken SHA-256 del token; el token mismo nunca llega a la base */
	@Override
	public void guardar(UUID idUsuario, String hashToken, Instant emision, Instant expiracion) {
		jdbc.sql("""
				INSERT INTO token_accion (id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion)
				VALUES (:usuario, :tipo, :hash, :emision, :expiracion)""")
				.param("usuario", idUsuario)
				.param("tipo", TIPO)
				.param("hash", hashToken)
				.param("emision", Timestamp.from(emision))
				.param("expiracion", Timestamp.from(expiracion))
				.update();
	}

	/** Se borran (no se marcan como usados) porque nunca se usaron: no hay nada que conservar. */
	@Override
	public void anularPendientes(UUID idUsuario) {
		jdbc.sql("DELETE FROM token_accion WHERE id_usuario = :usuario AND tipo = :tipo AND fecha_uso IS NULL")
				.param("usuario", idUsuario)
				.param("tipo", TIPO)
				.update();
	}

	@Override
	public Optional<TokenActivacion> buscarSinUsar(String hashToken) {
		return jdbc.sql("""
				SELECT id_token, id_usuario, fecha_expiracion FROM token_accion
				WHERE hash_token = :hash AND tipo = :tipo AND fecha_uso IS NULL""")
				.param("hash", hashToken)
				.param("tipo", TIPO)
				.query((fila, n) -> new TokenActivacion(fila.getObject("id_token", UUID.class),
						fila.getObject("id_usuario", UUID.class), fila.getTimestamp("fecha_expiracion").toInstant()))
				.optional();
	}

	/** La condición {@code fecha_uso IS NULL} impide que dos peticiones usen el mismo enlace. */
	@Override
	public boolean marcarUsado(UUID idToken, Instant fechaUso) {
		return jdbc.sql("UPDATE token_accion SET fecha_uso = :uso WHERE id_token = :id AND fecha_uso IS NULL")
				.param("uso", Timestamp.from(fechaUso))
				.param("id", idToken)
				.update() == 1;
	}

}
