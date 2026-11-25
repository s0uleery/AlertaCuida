package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.personal.PoliticaContrasena;

/** Lo que se le exige a una contraseña elegida con un enlace, al activar la cuenta o al recuperarla. */
final class ContrasenaNueva {

	private ContrasenaNueva() {
	}

	/**
	 * @param contrasena la que eligió la persona
	 * @param runHash    hash del RUN de su cuenta
	 * @param hashRun    calcula el hash para comparar sin descifrar el RUN guardado
	 * @throws SolicitudRechazada si no cumple la política o es el propio RUN
	 */
	static void revisar(String contrasena, String runHash, HashRun hashRun) {
		PoliticaContrasena.problema(contrasena).ifPresent(problema -> {
			throw new SolicitudRechazada(problema);
		});
		if (esElRun(contrasena, runHash, hashRun)) {
			throw new SolicitudRechazada("La contraseña no puede ser tu RUN");
		}
	}

	/** Se compara por el hash: no hace falta descifrar el RUN guardado. */
	private static boolean esElRun(String contrasena, String runHash, HashRun hashRun) {
		try {
			return hashRun.de(Run.de(contrasena)).equals(runHash);
		} catch (IllegalArgumentException noEsRun) {
			return false;
		}
	}

}
