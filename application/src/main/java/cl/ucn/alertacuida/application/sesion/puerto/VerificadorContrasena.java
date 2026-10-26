package cl.ucn.alertacuida.application.sesion.puerto;

/** Compara la contraseña escrita al iniciar sesión con el hash guardado (puerto del inicio de sesión). */
public interface VerificadorContrasena {

	/**
	 * Compara la contraseña con su hash. Con hash vacío (RUN inexistente o cuenta sin activar) devuelve false,
	 * pero tarda lo mismo: así el tiempo de respuesta no revela si el RUN existe.
	 */
	boolean coincide(String contrasena, String hash);

}
