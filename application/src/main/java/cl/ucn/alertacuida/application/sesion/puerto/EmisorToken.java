package cl.ucn.alertacuida.application.sesion.puerto;

import cl.ucn.alertacuida.application.sesion.modelo.TokenAcceso;
import cl.ucn.alertacuida.application.sesion.modelo.UsuarioSesion;

/** Crea el token de acceso que el dashboard manda en cada petición. */
public interface EmisorToken {

	/**
	 * @param usuario quién inició sesión, con sus permisos
	 * @return el token firmado y su vencimiento
	 */
	TokenAcceso emitir(UsuarioSesion usuario);

}
