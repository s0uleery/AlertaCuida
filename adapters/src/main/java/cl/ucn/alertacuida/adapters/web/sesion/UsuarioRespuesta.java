package cl.ucn.alertacuida.adapters.web.sesion;

import cl.ucn.alertacuida.application.sesion.modelo.UsuarioSesion;
import java.util.List;
import java.util.UUID;

/**
 * Usuario con sesión, como lo ve el dashboard.
 *
 * @param rol      ADMINISTRADOR, COORDINADOR o PERSONAL
 * @param permisos "MODULO:ACCION", ordenados para que la respuesta sea siempre igual
 */
public record UsuarioRespuesta(UUID id, String nombre, String rol, List<String> permisos) {

	/** @return el usuario del caso de uso en el formato JSON del dashboard */
	static UsuarioRespuesta de(UsuarioSesion usuario) {
		return new UsuarioRespuesta(usuario.id(), usuario.nombre(), usuario.codigoRol(),
				usuario.permisos().stream().sorted().toList());
	}

}
