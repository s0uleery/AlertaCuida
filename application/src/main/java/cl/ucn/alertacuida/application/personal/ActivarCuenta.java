package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.error.EnlaceInvalido;
import cl.ucn.alertacuida.application.personal.modelo.CuentaPendiente;
import cl.ucn.alertacuida.application.personal.modelo.TokenActivacion;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import cl.ucn.alertacuida.application.personal.puerto.HashContrasena;
import cl.ucn.alertacuida.application.personal.puerto.TokensActivacion;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.personal.PoliticaContrasena;
import java.time.Clock;
import java.time.Instant;

/** La persona abre su enlace y elige su contraseña (1.3.2). Desde ahí puede iniciar sesión. */
public class ActivarCuenta {

	private final TokensActivacion tokens;
	private final SecretosUnSoloUso secretos;
	private final CuentasPersonal cuentas;
	private final HashRun hashRun;
	private final HashContrasena hashContrasena;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	public ActivarCuenta(TokensActivacion tokens, SecretosUnSoloUso secretos, CuentasPersonal cuentas,
			HashRun hashRun, HashContrasena hashContrasena, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
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
	 * Revisa el enlace y la contraseña; si todo está bien, guarda la contraseña, activa la cuenta
	 * y marca el enlace como usado, en una transacción.
	 *
	 * @param token      lo que viene después de "#" en el enlace
	 * @param contrasena la que eligió la persona
	 * @param ip         desde dónde llegó la petición, para la auditoría
	 * @throws EnlaceInvalido      si el enlace no sirve
	 * @throws SolicitudRechazada si la contraseña no cumple la política; el enlace sigue sirviendo
	 */
	public void activar(String token, String contrasena, String ip) {
		Instant ahora = reloj.instant();
		TokenActivacion activacion = tokens.buscarSinUsar(secretos.hash(token))
				.filter(encontrado -> ahora.isBefore(encontrado.expiraEn()))
				.orElseThrow(EnlaceInvalido::new);
		CuentaPendiente cuenta = cuentas.buscarPendiente(activacion.idUsuario()).orElseThrow(EnlaceInvalido::new);
		PoliticaContrasena.problema(contrasena).ifPresent(problema -> {
			throw new SolicitudRechazada(problema);
		});
		if (esSuRun(contrasena, cuenta)) {
			throw new SolicitudRechazada("La contraseña no puede ser tu RUN");
		}
		String hash = hashContrasena.de(contrasena);
		transacciones.ejecutar(() -> {
			// Ambas condicionadas: si dos pestañas usan el mismo enlace a la vez, solo una gana
			if (!tokens.marcarUsado(activacion.id(), ahora) || !cuentas.activar(cuenta.id(), hash)) {
				throw new EnlaceInvalido();
			}
			auditoria.registrar(cuenta.id(), "MODIFICAR", "USUARIO", cuenta.id().toString(), ip, ahora);
			return null;
		});
	}

	/** Se compara por el hash: no hace falta descifrar el RUN guardado. */
	private boolean esSuRun(String contrasena, CuentaPendiente cuenta) {
		try {
			return hashRun.de(Run.de(contrasena)).equals(cuenta.runHash());
		} catch (IllegalArgumentException noEsRun) {
			return false;
		}
	}

}
