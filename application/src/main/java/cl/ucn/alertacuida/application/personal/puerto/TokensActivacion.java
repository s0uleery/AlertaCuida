package cl.ucn.alertacuida.application.personal.puerto;

import cl.ucn.alertacuida.application.personal.modelo.TokenActivacion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Enlaces de activación (token_accion de tipo ACTIVACION). Solo se guarda el hash del token. */
public interface TokensActivacion {

	/**
	 * @param idUsuario  cuenta a la que pertenece el enlace
	 * @param hashToken  hash del token, nunca el token
	 * @param emision    cuándo se emitió
	 * @param expiracion desde cuándo deja de servir
	 */
	void guardar(UUID idUsuario, String hashToken, Instant emision, Instant expiracion);

	/**
	 * Borra los enlaces sin usar de la cuenta: al emitir uno nuevo, los anteriores dejan de servir.
	 *
	 * @param idUsuario cuenta cuyos enlaces se anulan
	 */
	void anularPendientes(UUID idUsuario);

	/**
	 * @param hashToken hash del token que trae la persona
	 * @return el enlace sin usar con ese hash, aunque esté vencido: el vencimiento lo revisa el caso de uso
	 */
	Optional<TokenActivacion> buscarSinUsar(String hashToken);

	/**
	 * @param idToken  enlace que se está usando
	 * @param fechaUso cuándo se usó
	 * @return false si otra petición lo usó antes
	 */
	boolean marcarUsado(UUID idToken, Instant fechaUso);

}
