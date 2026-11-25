package cl.ucn.alertacuida.application.personal.puerto;

import cl.ucn.alertacuida.application.personal.modelo.TokenActivacion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Enlaces de recuperación de contraseña (token_accion de tipo RECUPERACION). Solo se guarda el hash del token.
 * A diferencia de la activación, los enlaces anteriores no se borran: se guardan para contar cuántos se
 * emitieron en la última hora, y solo sirve el más reciente de cada cuenta.
 */
public interface TokensRecuperacion {

	/**
	 * @param idUsuario  cuenta a la que pertenece el enlace
	 * @param hashToken  hash del token, nunca el token
	 * @param emision    cuándo se emitió
	 * @param expiracion desde cuándo deja de servir
	 */
	void guardar(UUID idUsuario, String hashToken, Instant emision, Instant expiracion);

	/**
	 * @param idUsuario cuenta revisada
	 * @param desde     inicio de la ventana
	 * @return cuántos enlaces se le emitieron desde ese instante, usados o no
	 */
	int emitidosDesde(UUID idUsuario, Instant desde);

	/**
	 * Borra los enlaces de la cuenta que vencieron antes de ese instante: ya no sirven ni cuentan.
	 *
	 * @param idUsuario cuenta revisada
	 * @param antesDe   límite
	 */
	void borrarVencidos(UUID idUsuario, Instant antesDe);

	/**
	 * @param hashToken hash del token que trae la persona
	 * @return el enlace sin usar con ese hash, solo si es el último que se le emitió a la cuenta, aunque esté
	 *         vencido: el vencimiento lo revisa el caso de uso
	 */
	Optional<TokenActivacion> buscarUltimoSinUsar(String hashToken);

	/**
	 * @param idToken  enlace que se está usando
	 * @param fechaUso cuándo se usó
	 * @return false si otra petición lo usó antes
	 */
	boolean marcarUsado(UUID idToken, Instant fechaUso);

}
