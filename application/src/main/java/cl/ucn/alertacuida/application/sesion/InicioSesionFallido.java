package cl.ucn.alertacuida.application.sesion;

/**
 * Cualquier falla del inicio de sesión: RUN inexistente, contraseña incorrecta, cuenta inactiva o bloqueada.
 * Es una sola para que el mensaje sea uniforme y no revele qué RUN existen (1.3.6).
 */
public class InicioSesionFallido extends RuntimeException {

	public InicioSesionFallido() {
		super("RUN o contraseña incorrectos");
	}

}
