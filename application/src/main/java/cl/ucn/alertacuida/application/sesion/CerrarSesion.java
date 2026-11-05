package cl.ucn.alertacuida.application.sesion;

import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.sesion.puerto.TokensSesion;

/**
 * Cierra la sesión de este navegador; las de otros computadores siguen abiertas. El token de acceso
 * no se puede revocar (el servidor no lo guarda), pero el dashboard lo olvida y dura pocos minutos.
 */
public class CerrarSesion {

	private final TokensSesion tokens;
	private final SecretosUnSoloUso secretos;

	/**
	 * @param tokens   dónde están los tokens de renovación
	 * @param secretos calcula el hash con que se busca el token
	 */
	public CerrarSesion(TokensSesion tokens, SecretosUnSoloUso secretos) {
		this.tokens = tokens;
		this.secretos = secretos;
	}

	/** @param secreto token de renovación que trae el navegador; si falta, no hay nada que cerrar */
	public void cerrar(String secreto) {
		if (secreto != null && !secreto.isBlank()) {
			tokens.eliminar(secretos.hash(secreto));
		}
	}

}
