package cl.ucn.alertacuida.adapters.persistencia.personal;

import cl.ucn.alertacuida.application.personal.modelo.TokenActivacion;
import cl.ucn.alertacuida.application.personal.puerto.TokensRecuperacion;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Enlaces de recuperación de contraseña en token_accion (tipo RECUPERACION). Los anteriores no se borran al
 * emitir uno nuevo, para contar cuántos se enviaron en la última hora: en cambio, solo sirve el más reciente.
 */
@Repository
public class TokensRecuperacionJdbc implements TokensRecuperacion {

	private static final String TIPO = "RECUPERACION";

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public TokensRecuperacionJdbc(JdbcClient jdbc) {
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

	@Override
	public int emitidosDesde(UUID idUsuario, Instant desde) {
		return jdbc.sql("""
				SELECT count(*) FROM token_accion
				WHERE id_usuario = :usuario AND tipo = :tipo AND fecha_emision >= :desde""")
				.param("usuario", idUsuario)
				.param("tipo", TIPO)
				.param("desde", Timestamp.from(desde))
				.query(Integer.class)
				.single();
	}

	@Override
	public void borrarVencidos(UUID idUsuario, Instant antesDe) {
		jdbc.sql("DELETE FROM token_accion WHERE id_usuario = :usuario AND tipo = :tipo AND fecha_expiracion < :antes")
				.param("usuario", idUsuario)
				.param("tipo", TIPO)
				.param("antes", Timestamp.from(antesDe))
				.update();
	}

	/** El más reciente se decide por la hora de emisión y, si coinciden, por el id. */
	@Override
	public Optional<TokenActivacion> buscarUltimoSinUsar(String hashToken) {
		return jdbc.sql("""
				SELECT t.id_token, t.id_usuario, t.fecha_expiracion FROM token_accion t
				WHERE t.hash_token = :hash AND t.tipo = :tipo AND t.fecha_uso IS NULL
					AND NOT EXISTS (SELECT 1 FROM token_accion o
						WHERE o.id_usuario = t.id_usuario AND o.tipo = t.tipo
							AND (o.fecha_emision, o.id_token) > (t.fecha_emision, t.id_token))""")
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
