package cl.ucn.alertacuida.application.personal.puerto;

/**
 * Calcula el hash con que se guarda una contraseña nueva. La contraseña nunca se guarda tal cual:
 * al iniciar sesión se compara con este hash (ver {@code VerificadorContrasena}).
 */
public interface HashContrasena {

	/**
	 * @param contrasena la que eligió la persona, ya validada con la política
	 * @return el hash, que incluye su propia sal
	 */
	String de(String contrasena);

}
