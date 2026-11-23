package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.personal.puerto.EquipoPersonal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Enciende o apaga el aviso por correo de las alertas técnicas de una cuenta (usuario.recibe_avisos).
 * Lo cambia solo quien administra el equipo, en cualquier cuenta, también la propia.
 */
public class CambiarRecibeAvisos {

	private final EquipoPersonal equipo;
	private final RegistroAuditoria auditoria;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public CambiarRecibeAvisos(EquipoPersonal equipo, RegistroAuditoria auditoria, Clock reloj) {
		this.equipo = equipo;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/**
	 * @param idCuenta cuenta que se cambia
	 * @param recibe   si desde ahora recibe los avisos técnicos por correo
	 * @param idQuien  quien hace el cambio, para la auditoría
	 * @param ip       desde dónde llegó la petición, para la auditoría
	 * @throws NoEncontrado si la cuenta no existe
	 */
	public void cambiar(UUID idCuenta, boolean recibe, UUID idQuien, String ip) {
		if (!equipo.cambiarRecibeAvisos(idCuenta, recibe)) {
			throw new NoEncontrado("No existe esa cuenta del equipo");
		}
		auditoria.registrar(idQuien, "MODIFICAR", "USUARIO", idCuenta.toString(), ip, reloj.instant(),
				Map.of("recibeAvisos", recibe));
	}

}
