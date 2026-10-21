package cl.ucn.alertacuida.adapters.web.sesion;

import cl.ucn.alertacuida.application.sesion.UsuarioSesion;
import java.util.List;
import java.util.UUID;

public record UsuarioRespuesta(UUID id, String nombre, String rol, List<String> permisos) {

	static UsuarioRespuesta de(UsuarioSesion usuario) {
		return new UsuarioRespuesta(usuario.id(), usuario.nombre(), usuario.codigoRol(),
				usuario.permisos().stream().sorted().toList());
	}

}
