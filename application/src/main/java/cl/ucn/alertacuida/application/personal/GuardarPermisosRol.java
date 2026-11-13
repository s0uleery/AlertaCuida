package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.modelo.RolConPermisos;
import cl.ucn.alertacuida.application.personal.puerto.RolesPermisos;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reemplaza los permisos de un rol. Los del administrador (rol de sistema) no se tocan, para que
 * siempre haya alguien que administre las cuentas. Rigen cuando cada persona vuelve a iniciar sesión.
 */
public class GuardarPermisosRol {

	/** Módulos de permiso_rol (CHECK de V1). */
	private static final Set<String> MODULOS = Set.of("ALERTAS", "USUARIOS", "REPORTES", "ADMINISTRACION");

	/** Acciones de permiso_rol (CHECK de V1). */
	private static final Set<String> ACCIONES = Set.of("VER", "CREAR", "EDITAR", "ELIMINAR", "EXPORTAR");

	private final RolesPermisos roles;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public GuardarPermisosRol(RolesPermisos roles, RegistroAuditoria auditoria, Transacciones transacciones,
			Clock reloj) {
		this.roles = roles;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Deja al rol con exactamente esos permisos. Si no cambia nada, no lo audita.
	 *
	 * @param codigo   rol que se edita
	 * @param permisos "MODULO:ACCION"
	 * @param idQuien  quien hace el cambio, para la auditoría
	 * @param ip       desde dónde llegó la petición, para la auditoría
	 * @throws SolicitudRechazada si un permiso no existe
	 * @throws NoEncontrado       si el rol no existe o está desactivado
	 * @throws Conflicto          si es el rol de sistema (administrador)
	 */
	public void guardar(String codigo, Set<String> permisos, UUID idQuien, String ip) {
		permisos.forEach(GuardarPermisosRol::validar);
		transacciones.ejecutar(() -> {
			RolConPermisos rol = roles.buscar(codigo).orElseThrow(() -> new NoEncontrado("No existe ese rol"));
			if (rol.esSistema()) {
				throw new Conflicto("Los permisos del administrador no se cambian");
			}
			List<String> agregados = permisos.stream().filter(p -> !rol.permisos().contains(p)).sorted().toList();
			List<String> quitados = rol.permisos().stream().filter(p -> !permisos.contains(p)).sorted().toList();
			if (agregados.isEmpty() && quitados.isEmpty()) {
				return null;
			}
			roles.reemplazarPermisos(codigo, permisos);
			Map<String, Object> detalle = new LinkedHashMap<>();
			detalle.put("agregados", agregados);
			detalle.put("quitados", quitados);
			auditoria.registrar(idQuien, "MODIFICAR", "PERMISO_ROL", codigo, ip, reloj.instant(), detalle);
			return null;
		});
	}

	/** @throws SolicitudRechazada si no es "MODULO:ACCION" con un módulo y una acción conocidos */
	private static void validar(String permiso) {
		String[] partes = permiso == null ? new String[0] : permiso.split(":", -1);
		if (partes.length != 2 || !MODULOS.contains(partes[0]) || !ACCIONES.contains(partes[1])) {
			throw new SolicitudRechazada("Permiso desconocido: " + permiso);
		}
	}

}
