package cl.ucn.alertacuida.application.personal.puerto;

import cl.ucn.alertacuida.application.personal.modelo.RolConPermisos;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Roles activos y sus permisos (tablas rol y permiso_rol). */
public interface RolesPermisos {

	/** @return los roles activos con sus permisos, el de sistema (administrador) primero */
	List<RolConPermisos> listar();

	/**
	 * @param codigo por ejemplo, PERSONAL
	 * @return el rol con sus permisos, si existe y está activo
	 */
	Optional<RolConPermisos> buscar(String codigo);

	/**
	 * Deja al rol exactamente con esos permisos.
	 *
	 * @param permisos "MODULO:ACCION", ya validados
	 */
	void reemplazarPermisos(String codigo, Set<String> permisos);

}
