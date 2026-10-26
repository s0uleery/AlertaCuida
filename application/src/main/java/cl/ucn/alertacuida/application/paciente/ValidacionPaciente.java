package cl.ucn.alertacuida.application.paciente;

import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.domain.comun.Run;
import java.time.LocalDate;

/**
 * Reglas comunes al alta y a la edición de un paciente: RUN válido, nombres presentes y fechas
 * que no estén en el futuro. Deja los textos sin espacios sobrantes y los vacíos como null.
 *
 * <p>En los mensajes se habla de "usuario", como se les dice a los pacientes en el PRLAC.
 */
final class ValidacionPaciente {

	/** Solo métodos estáticos: no se crean objetos de esta clase. */
	private ValidacionPaciente() {
	}

	/**
	 * @param datos lo que escribió el personal
	 * @param hoy   fecha de hoy en Chile
	 * @return los datos limpios, con el RUN normalizado
	 * @throws SolicitudRechazada si un dato no cumple una regla
	 */
	static DatosPaciente validar(DatosPaciente datos, LocalDate hoy) {
		Run run;
		try {
			run = Run.de(datos.run());
		} catch (IllegalArgumentException invalido) {
			throw new SolicitudRechazada(invalido.getMessage());
		}
		String nombres = obligatorio(datos.nombres(), "Los nombres son obligatorios");
		String apellidos = obligatorio(datos.apellidos(), "Los apellidos son obligatorios");
		if (datos.fechaIngreso() == null) {
			throw new SolicitudRechazada("La fecha de ingreso es obligatoria");
		}
		noFutura(datos.fechaNacimiento(), hoy, "La fecha de nacimiento no puede ser futura");
		noFutura(datos.fechaConsentimiento(), hoy, "La fecha del consentimiento no puede ser futura");
		noFutura(datos.fechaIngreso(), hoy, "La fecha de ingreso no puede ser futura");
		return new DatosPaciente(run.valor(), nombres, apellidos, opcional(datos.alias()), datos.fechaNacimiento(),
				datos.sexo(), opcional(datos.direccion()), datos.nivelDependencia(), datos.fechaConsentimiento(),
				datos.fechaIngreso());
	}

	/**
	 * @return el texto sin espacios sobrantes
	 * @throws SolicitudRechazada con {@code mensaje} si quedó vacío
	 */
	private static String obligatorio(String texto, String mensaje) {
		String limpio = opcional(texto);
		if (limpio == null) {
			throw new SolicitudRechazada(mensaje);
		}
		return limpio;
	}

	/** @return el texto sin espacios sobrantes, o null si quedó vacío */
	private static String opcional(String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
	}

	/** @throws SolicitudRechazada con {@code mensaje} si la fecha es posterior a hoy (una fecha vacía se acepta) */
	private static void noFutura(LocalDate fecha, LocalDate hoy, String mensaje) {
		if (fecha != null && fecha.isAfter(hoy)) {
			throw new SolicitudRechazada(mensaje);
		}
	}

}
