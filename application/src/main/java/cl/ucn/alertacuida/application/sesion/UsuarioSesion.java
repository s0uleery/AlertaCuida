package cl.ucn.alertacuida.application.sesion;

import java.util.Set;
import java.util.UUID;

/**
 * Usuario con sesión iniciada.
 *
 * @param permisos del rol, como "MODULO:ACCION" (ej. "ALERTAS:VER")
 */
public record UsuarioSesion(UUID id, String nombre, String codigoRol, Set<String> permisos) {

	public UsuarioSesion {
		permisos = Set.copyOf(permisos);
	}

}
