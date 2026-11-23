package cl.ucn.alertacuida.application.sesion.puerto;

import cl.ucn.alertacuida.application.sesion.modelo.TokenSesion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tokens de renovación del dashboard (tabla token_sesion). Solo se guarda el hash del token. */
public interface TokensSesion {

	/**
	 * @param idUsuario  dueño de la sesión
	 * @param hashToken  hash del token, nunca el token
	 * @param emision    cuándo se emitió
	 * @param expiracion límite de la sesión
	 */
	void guardar(UUID idUsuario, String hashToken, Instant emision, Instant expiracion);

	/**
	 * @param hashToken hash del token que trae el navegador
	 * @return el token con ese hash, aunque esté vencido o revocado: eso lo revisa el caso de uso
	 */
	Optional<TokenSesion> buscar(String hashToken);

	/**
	 * Marca un token como reemplazado, sin borrarlo: así se reconoce si alguien intenta reutilizarlo.
	 *
	 * @param idToken token que deja de servir
	 * @param fecha   cuándo
	 */
	void revocar(UUID idToken, Instant fecha);

	/**
	 * Termina todas las sesiones de la cuenta. Borra las filas en vez de revocarlas: un token revocado
	 * hace poco todavía se aceptaría por la gracia de la rotación.
	 *
	 * @param idUsuario cuenta afectada
	 */
	void eliminarTodos(UUID idUsuario);

	/**
	 * Borra un token al cerrar sesión. Al no quedar fila, no se le aplica la gracia de la rotación.
	 *
	 * @param hashToken hash del token que trae el navegador
	 */
	void eliminar(String hashToken);

	/**
	 * Limpia los tokens de todas las cuentas que ya alcanzaron su límite.
	 *
	 * @param ahora hora actual
	 */
	void eliminarVencidos(Instant ahora);

}
