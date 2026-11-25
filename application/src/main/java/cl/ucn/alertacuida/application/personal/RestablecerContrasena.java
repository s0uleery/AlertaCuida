package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.error.EnlaceInvalido;
import cl.ucn.alertacuida.application.personal.modelo.CuentaRecuperable;
import cl.ucn.alertacuida.application.personal.modelo.TokenActivacion;
import cl.ucn.alertacuida.application.personal.puerto.CuentasRecuperacion;
import cl.ucn.alertacuida.application.personal.puerto.HashContrasena;
import cl.ucn.alertacuida.application.personal.puerto.TokensRecuperacion;
import java.time.Clock;
import java.time.Instant;

/**
 * La persona abre el enlace de recuperación y elige su contraseña nueva. La anterior deja de servir. No
 * reactiva una cuenta desactivada.
 */
public class RestablecerContrasena {

	private final TokensRecuperacion tokens;
	private final SecretosUnSoloUso secretos;
	private final CuentasRecuperacion cuentas;
	private final HashRun hashRun;
	private final HashContrasena hashContrasena;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public RestablecerContrasena(TokensRecuperacion tokens, SecretosUnSoloUso secretos, CuentasRecuperacion cuentas,
			HashRun hashRun, HashContrasena hashContrasena, RegistroAuditoria auditoria, Transacciones transacciones,
			Clock reloj) {
		this.tokens = tokens;
		this.secretos = secretos;
		this.cuentas = cuentas;
		this.hashRun = hashRun;
		this.hashContrasena = hashContrasena;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Revisa el enlace y la contraseña; si todo está bien, la cambia y marca el enlace como usado, en una
	 * transacción.
	 *
	 * @param token      lo que viene después de "#" en el enlace
	 * @param contrasena la nueva
	 * @param ip         desde dónde llegó la petición, para la auditoría
	 * @throws EnlaceInvalido      si el enlace no sirve (usado, vencido, reemplazado o de una cuenta inactiva)
	 * @throws SolicitudRechazada si la contraseña no cumple la política; el enlace sigue sirviendo
	 */
	public void restablecer(String token, String contrasena, String ip) {
		Instant ahora = reloj.instant();
		TokenActivacion enlace = tokens.buscarUltimoSinUsar(secretos.hash(token))
				.filter(encontrado -> ahora.isBefore(encontrado.expiraEn()))
				.orElseThrow(EnlaceInvalido::new);
		CuentaRecuperable cuenta = cuentas.buscarActiva(enlace.idUsuario()).orElseThrow(EnlaceInvalido::new);
		ContrasenaNueva.revisar(contrasena, cuenta.runHash(), hashRun);
		String hash = hashContrasena.de(contrasena);
		transacciones.ejecutar(() -> {
			// Ambas condicionadas: si dos pestañas usan el mismo enlace a la vez, solo una gana
			if (!tokens.marcarUsado(enlace.id(), ahora) || !cuentas.cambiarContrasena(cuenta.id(), hash)) {
				throw new EnlaceInvalido();
			}
			auditoria.registrar(cuenta.id(), "MODIFICAR", "USUARIO", cuenta.id().toString(), ip, ahora);
			return null;
		});
	}

}
