package cl.ucn.alertacuida.application.reloj;

import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.reloj.modelo.DatosReloj;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;

/**
 * Reglas comunes al registro y a la edición de un reloj: identificador obligatorio, textos sin
 * espacios sobrantes (vacío = null) y estado OPERATIVO si no se indica.
 */
final class ValidacionReloj {

	private ValidacionReloj() {
	}

	/**
	 * @param datos lo que escribió el personal
	 * @return los datos limpios
	 * @throws SolicitudRechazada si falta el identificador
	 */
	static DatosReloj validar(DatosReloj datos) {
		String identificador = opcional(datos.identificadorReloj());
		if (identificador == null) {
			throw new SolicitudRechazada("El identificador del reloj es obligatorio");
		}
		EstadoReloj estado = datos.estado() == null ? EstadoReloj.OPERATIVO : datos.estado();
		return new DatosReloj(identificador, opcional(datos.modelo()), opcional(datos.iccid()),
				opcional(datos.numeroSim()), datos.fechaVencimientoPlan(), estado);
	}

	/** @return el texto sin espacios sobrantes, o null si quedó vacío */
	private static String opcional(String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
	}

}
