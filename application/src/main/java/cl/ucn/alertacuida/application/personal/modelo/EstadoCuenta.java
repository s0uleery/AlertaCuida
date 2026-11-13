package cl.ucn.alertacuida.application.personal.modelo;

/** En qué punto está la cuenta de una persona del equipo. Sale de usuario.activo y hash_contrasena. */
public enum EstadoCuenta {

	/** Ya eligió su contraseña y puede entrar. */
	ACTIVA,

	/** Todavía no usa su enlace de activación: no tiene contraseña. */
	PENDIENTE,

	/** Tenía contraseña y se le quitó el acceso. */
	DESACTIVADA;

	/**
	 * @param activo        usuario.activo
	 * @param tieneClave    si hash_contrasena no está vacío
	 * @return el estado que corresponde a esas dos columnas
	 */
	public static EstadoCuenta de(boolean activo, boolean tieneClave) {
		if (activo) {
			return ACTIVA;
		}
		return tieneClave ? DESACTIVADA : PENDIENTE;
	}

}
