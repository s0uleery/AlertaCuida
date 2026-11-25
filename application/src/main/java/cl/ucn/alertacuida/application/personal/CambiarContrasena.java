package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.modelo.CuentaRecuperable;
import cl.ucn.alertacuida.application.personal.puerto.ContrasenasGuardadas;
import cl.ucn.alertacuida.application.personal.puerto.CuentasRecuperacion;
import cl.ucn.alertacuida.application.personal.puerto.HashContrasena;
import cl.ucn.alertacuida.application.sesion.PoliticaBloqueo;
import cl.ucn.alertacuida.application.sesion.puerto.RegistroAccesos;
import cl.ucn.alertacuida.application.sesion.puerto.VerificadorContrasena;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * La persona cambia su contraseña desde "Mi perfil" (decisión del 25-11-2026): escribe la actual y la nueva. La
 * actual incorrecta cuenta como intento fallido, igual que en el inicio de sesión, así que también bloquea la
 * cuenta tras varios intentos: nadie puede probar contraseñas con una sesión olvidada abierta.
 */
public class CambiarContrasena {

	private final CuentasRecuperacion cuentas;
	private final ContrasenasGuardadas guardadas;
	private final VerificadorContrasena verificador;
	private final HashContrasena hashContrasena;
	private final HashRun hashRun;
	private final RegistroAccesos accesos;
	private final PoliticaBloqueo bloqueo;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public CambiarContrasena(CuentasRecuperacion cuentas, ContrasenasGuardadas guardadas,
			VerificadorContrasena verificador, HashContrasena hashContrasena, HashRun hashRun, RegistroAccesos accesos,
			PoliticaBloqueo bloqueo, RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.cuentas = cuentas;
		this.guardadas = guardadas;
		this.verificador = verificador;
		this.hashContrasena = hashContrasena;
		this.hashRun = hashRun;
		this.accesos = accesos;
		this.bloqueo = bloqueo;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param idSesion la cuenta de la sesión (del token)
	 * @param actual   la contraseña que tiene ahora
	 * @param nueva    la que elige
	 * @param ip       desde dónde llegó la petición, para la auditoría
	 * @throws NoEncontrado       si la cuenta ya no está activa
	 * @throws SolicitudRechazada si la cuenta está bloqueada, la actual no es correcta, la nueva es igual a la
	 *                            actual o no cumple la política
	 */
	public void cambiar(UUID idSesion, String actual, String nueva, String ip) {
		Instant ahora = reloj.instant();
		CuentaRecuperable cuenta = cuentas.buscarActiva(idSesion)
				.orElseThrow(() -> new NoEncontrado("No existe esa cuenta del equipo"));
		if (bloqueo.bloquea(accesos.contarFallidosRecientes(cuenta.id(), bloqueo.inicioVentana(ahora)))) {
			throw new SolicitudRechazada("Hubo demasiados intentos con una contraseña incorrecta. Espera unos minutos "
					+ "y vuelve a intentarlo");
		}
		String hashActual = guardadas.hashDe(cuenta.id())
				.orElseThrow(() -> new NoEncontrado("No existe esa cuenta del equipo"));
		if (actual == null || !verificador.coincide(actual, hashActual)) {
			accesos.registrarLoginFallido(cuenta.id(), ip, ahora);
			throw new SolicitudRechazada("La contraseña actual no es correcta");
		}
		if (verificador.coincide(nueva, hashActual)) {
			throw new SolicitudRechazada("La contraseña nueva debe ser distinta de la actual");
		}
		ContrasenaNueva.revisar(nueva, cuenta.runHash(), hashRun);
		String hash = hashContrasena.de(nueva);
		transacciones.ejecutar(() -> {
			if (!cuentas.cambiarContrasena(cuenta.id(), hash)) {
				throw new NoEncontrado("No existe esa cuenta del equipo");
			}
			auditoria.registrar(cuenta.id(), "MODIFICAR", "USUARIO", cuenta.id().toString(), ip, ahora,
					Map.<String, Object>of("contrasena", "cambiada"));
			return null;
		});
	}

}
