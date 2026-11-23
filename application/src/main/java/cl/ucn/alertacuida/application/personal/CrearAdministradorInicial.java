package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.personal.error.CuentaDuplicada;
import cl.ucn.alertacuida.application.personal.modelo.DatosCuenta;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import java.util.Optional;

/**
 * Primer administrador de un servidor recién instalado, que no tiene datos de ejemplo y por lo tanto
 * ninguna cuenta con la que entrar al dashboard. Usa el mismo alta que el resto del equipo: la cuenta
 * queda pendiente y la persona elige su contraseña con el enlace de activación. Si ya existe un
 * administrador, no hace nada, así que puede correr en cada arranque.
 */
public class CrearAdministradorInicial {

	private static final String ROL = "ADMINISTRADOR";

	private final CuentasPersonal cuentas;
	private final CrearCuentaPersonal crear;

	/** Recibe el puerto de las cuentas y el alta del personal; los arma ConfiguracionCasosDeUso. */
	public CrearAdministradorInicial(CuentasPersonal cuentas, CrearCuentaPersonal crear) {
		this.cuentas = cuentas;
		this.crear = crear;
	}

	/**
	 * Crea la cuenta solo si todavía no hay ningún administrador. En la auditoría queda sin usuario y
	 * sin IP: la crea el servidor al arrancar, no una persona.
	 *
	 * @param run    RUN del administrador, con o sin puntos
	 * @param nombre nombre que se muestra en el dashboard
	 * @param correo correo de la cuenta
	 * @return el enlace de activación, o vacío si ya había un administrador
	 * @throws SolicitudRechazada si el RUN no es válido
	 * @throws CuentaDuplicada    si ya hay otra cuenta con ese RUN o correo
	 */
	public Optional<EnlaceActivacion> crearSiFalta(String run, String nombre, String correo) {
		if (cuentas.existeAdministrador()) {
			return Optional.empty();
		}
		DatosCuenta datos = new DatosCuenta(run, nombre, null, correo, ROL, null, false, false);
		return Optional.of(crear.crear(datos, null, null));
	}

}
