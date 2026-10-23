package cl.ucn.alertacuida.application.sesion.error;

/**
 * Cualquier falla del inicio de sesión: RUN inexistente, contraseña incorrecta, cuenta inactiva o bloqueada.
 * Es una sola para que el mensaje sea uniforme y no revele qué RUN existen.
 */
public class InicioSesionFallido extends RuntimeException {

	/** Crea el error con el mensaje uniforme "RUN o contraseña incorrectos". */
	public InicioSesionFallido() {
		super("RUN o contraseña incorrectos");
	}

}
