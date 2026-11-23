package cl.ucn.alertacuida.application.sesion;

import cl.ucn.alertacuida.application.sesion.modelo.TokenSesion;
import java.time.Duration;
import java.time.Instant;

/**
 * Cuánto puede durar una sesión.
 *
 * @param duracionMaxima tiempo desde el login hasta tener que escribir la contraseña otra vez
 * @param inactividad    tiempo sin renovar tras el cual la sesión termina
 * @param graciaRotacion tiempo en que un token recién reemplazado todavía se acepta, porque dos
 *                       pestañas pueden renovar a la vez con la misma cookie
 */
public record PoliticaSesion(Duration duracionMaxima, Duration inactividad, Duration graciaRotacion) {

	/** @return cuándo vence una sesión que parte en {@code inicio} */
	Instant vencimiento(Instant inicio) {
		return inicio.plus(duracionMaxima);
	}

	/** @return true si la sesión ya alcanzó su límite */
	boolean vencida(TokenSesion token, Instant ahora) {
		return !ahora.isBefore(token.expiraEn());
	}

	/** @return true si pasó demasiado tiempo desde la última renovación */
	boolean inactiva(TokenSesion token, Instant ahora) {
		return !ahora.isBefore(token.emitidoEn().plus(inactividad));
	}

	/** @return true si el token se reemplazó hace tan poco que aún puede ser otra pestaña */
	boolean enGracia(TokenSesion token, Instant ahora) {
		return ahora.isBefore(token.revocadoEn().plus(graciaRotacion));
	}

}
