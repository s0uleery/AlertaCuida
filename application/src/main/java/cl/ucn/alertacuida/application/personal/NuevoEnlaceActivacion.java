package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.error.CuentaNoPendiente;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Otro enlace para una cuenta pendiente, si el anterior se perdió o venció. El anterior deja de servir. */
public class NuevoEnlaceActivacion {

	private final CuentasPersonal cuentas;
	private final EmisorEnlacesActivacion enlaces;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public NuevoEnlaceActivacion(CuentasPersonal cuentas, EmisorEnlacesActivacion enlaces,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.cuentas = cuentas;
		this.enlaces = enlaces;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param idUsuario       cuenta pendiente que necesita otro enlace
	 * @param idAdministrador quien lo pide, para la auditoría
	 * @param ip              desde dónde llegó la petición, para la auditoría
	 * @return el enlace nuevo; el anterior ya no sirve
	 * @throws CuentaNoPendiente si la cuenta no existe o ya tiene contraseña
	 */
	public EnlaceActivacion emitir(UUID idUsuario, UUID idAdministrador, String ip) {
		cuentas.buscarPendiente(idUsuario).orElseThrow(CuentaNoPendiente::new);
		Instant ahora = reloj.instant();
		return transacciones.ejecutar(() -> {
			auditoria.registrar(idAdministrador, "MODIFICAR", "USUARIO", idUsuario.toString(), ip, ahora);
			return enlaces.emitir(idUsuario, ahora);
		});
	}

}
