package cl.ucn.alertacuida.application.configuracion;

import java.util.UUID;

/**
 * La versión de la configuración que rige hoy para un paciente. La usa la sincronización para
 * avisar al reloj que debe descargarla, sin conocer cómo se arma.
 */
public interface VersionVigente {

	/**
	 * @return la versión vigente del paciente
	 * @throws cl.ucn.alertacuida.application.comun.error.NoEncontrado si el paciente no existe
	 */
	String de(UUID idPaciente);

}
