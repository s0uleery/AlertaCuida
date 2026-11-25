package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.personal.modelo.MiCuenta;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPropias;
import java.util.UUID;

/**
 * Datos de la cuenta propia para "Mi cuenta", donde cada persona del equipo enciende o apaga su WhatsApp
 * (decisión del 24-11-2026). El cambio lo hace {@link CambiarTelefonoPersonal}, sobre la cuenta de la sesión.
 */
public class ConsultarMiCuenta {

	private final CuentasPropias cuentas;

	/** @param cuentas lectura de la cuenta propia */
	public ConsultarMiCuenta(CuentasPropias cuentas) {
		this.cuentas = cuentas;
	}

	/**
	 * @param idSesion la cuenta de quien tiene la sesión (del token, nunca del cuerpo de la petición)
	 * @return sus datos
	 * @throws NoEncontrado si la cuenta ya no existe
	 */
	public MiCuenta consultar(UUID idSesion) {
		return cuentas.buscar(idSesion).orElseThrow(() -> new NoEncontrado("No existe esa cuenta del equipo"));
	}

}
