package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.modelo.CambioRol;
import cl.ucn.alertacuida.application.personal.modelo.CuentaEquipo;
import cl.ucn.alertacuida.application.personal.modelo.RolConPermisos;
import cl.ucn.alertacuida.application.personal.puerto.EquipoPersonal;
import cl.ucn.alertacuida.application.personal.puerto.RolesPermisos;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cambio del rol de una cuenta del equipo. El rol nuevo rige desde que la persona vuelve a
 * iniciar sesión, porque los permisos viajan en su token.
 */
public class CambiarRolPersonal {

	private final EquipoPersonal equipo;
	private final RolesPermisos roles;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public CambiarRolPersonal(EquipoPersonal equipo, RolesPermisos roles, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		this.equipo = equipo;
		this.roles = roles;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Revisa las reglas y guarda el rol, y el teléfono si se escribió uno. Si el rol no cambia,
	 * no hace nada.
	 *
	 * @param idCuenta cuenta a la que se le cambia el rol
	 * @param cambio   rol nuevo y, al pasar a coordinador, su teléfono
	 * @param idQuien  quien hace el cambio, para la auditoría
	 * @param ip       desde dónde llegó la petición, para la auditoría
	 * @throws NoEncontrado       si la cuenta no existe
	 * @throws SolicitudRechazada si el rol no existe o está desactivado, o el teléfono no sirve
	 * @throws Conflicto          si es la cuenta propia o dejaría el sistema sin administrador activo
	 */
	public void cambiar(UUID idCuenta, CambioRol cambio, UUID idQuien, String ip) {
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			CuentaEquipo cuenta = equipo.buscar(idCuenta)
					.orElseThrow(() -> new NoEncontrado("No existe esa cuenta del equipo"));
			if (cuenta.id().equals(idQuien)) {
				throw new Conflicto("No puedes cambiar tu propio rol: pídeselo a otro administrador");
			}
			roles.buscar(cambio.codigoRol())
					.orElseThrow(() -> new SolicitudRechazada("El rol no existe o está desactivado"));
			if (cuenta.codigoRol().equals(cambio.codigoRol())) {
				return null;
			}
			if (dejaSinAdministrador(cuenta)) {
				throw new Conflicto("Es el único administrador activo: primero da ese rol a otra cuenta");
			}
			String telefono = TelefonoAvisos.validar(cambio.codigoRol(), cambio.telefono(), cambio.aceptaWhatsapp(),
					cuenta.tieneTelefono());
			equipo.cambiarRol(idCuenta, cambio.codigoRol());
			Map<String, Object> detalle = new LinkedHashMap<>();
			detalle.put("rolAnterior", cuenta.codigoRol());
			detalle.put("rolNuevo", cambio.codigoRol());
			if (telefono != null) {
				equipo.guardarTelefono(idCuenta, telefono, cambio.aceptaWhatsapp() ? ahora : null);
				detalle.put("telefonoAgregado", true);
			}
			auditoria.registrar(idQuien, "MODIFICAR", "USUARIO", idCuenta.toString(), ip, ahora, detalle);
			return null;
		});
	}

	/** @return si la cuenta es el último administrador activo (rol de sistema) y deja de serlo */
	private boolean dejaSinAdministrador(CuentaEquipo cuenta) {
		boolean esAdministrador = roles.buscar(cuenta.codigoRol()).map(RolConPermisos::esSistema).orElse(false);
		return esAdministrador && cuenta.activa() && !equipo.hayOtroAdministradorActivo(cuenta.id());
	}

}
