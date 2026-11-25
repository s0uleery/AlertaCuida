package cl.ucn.alertacuida.application.personal.puerto;

import cl.ucn.alertacuida.application.personal.modelo.CuentaRecuperable;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuentas del equipo tal como las necesita la recuperación de contraseña. Solo cuentan las activas y con
 * contraseña: una pendiente tiene su enlace de activación, y una desactivada no debe volver a entrar.
 */
public interface CuentasRecuperacion {

	/**
	 * @param runHash HMAC del RUN normalizado
	 * @return la cuenta activa con ese RUN, si existe
	 */
	Optional<CuentaRecuperable> buscarActivaPorRunHash(String runHash);

	/**
	 * @param id cuenta buscada
	 * @return la cuenta, solo si sigue activa
	 */
	Optional<CuentaRecuperable> buscarActiva(UUID id);

	/**
	 * Reemplaza la contraseña, solo si la cuenta sigue activa.
	 *
	 * @param hashContrasena hash de la contraseña nueva, nunca la contraseña
	 * @return si la cambió
	 */
	boolean cambiarContrasena(UUID id, String hashContrasena);

}
