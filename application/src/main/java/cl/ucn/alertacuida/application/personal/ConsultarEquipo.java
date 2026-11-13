package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.personal.modelo.PerfilEquipo;
import cl.ucn.alertacuida.application.personal.modelo.RolConPermisos;
import cl.ucn.alertacuida.application.personal.puerto.EquipoPersonal;
import cl.ucn.alertacuida.application.personal.puerto.RolesPermisos;
import java.util.List;

/** Lo que muestra la sección Administración: las cuentas del equipo y los roles con sus permisos. */
public class ConsultarEquipo {

	private final EquipoPersonal equipo;
	private final RolesPermisos roles;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public ConsultarEquipo(EquipoPersonal equipo, RolesPermisos roles) {
		this.equipo = equipo;
		this.roles = roles;
	}

	/** @return las cuentas del equipo, por nombre */
	public List<PerfilEquipo> perfiles() {
		return equipo.listar();
	}

	/** @return los roles activos con sus permisos, el administrador primero */
	public List<RolConPermisos> roles() {
		return roles.listar();
	}

}
