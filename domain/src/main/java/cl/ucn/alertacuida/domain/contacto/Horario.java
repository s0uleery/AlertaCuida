package cl.ucn.alertacuida.domain.contacto;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Horario en que un contacto puede recibir avisos, en hora de Chile (contacto_paciente.hora_inicio
 * y hora_fin). Si el inicio es posterior al fin, cruza la medianoche (22:00 a 07:00). Sin horario
 * (vacío) el contacto está disponible todo el día.
 *
 * @param inicio desde esta hora, incluida
 * @param fin    hasta esta hora, sin incluirla
 */
public record Horario(LocalTime inicio, LocalTime fin) {

	/**
	 * @return el horario, en minutos exactos; o vacío (todo el día) si no viene ninguna hora
	 * @throws IllegalArgumentException si viene solo una de las horas, o las dos son iguales
	 */
	public static Horario de(LocalTime inicio, LocalTime fin) {
		if (inicio == null && fin == null) {
			return null;
		}
		if (inicio == null || fin == null) {
			throw new IllegalArgumentException("El horario necesita la hora de inicio y la de término");
		}
		LocalTime desde = inicio.truncatedTo(ChronoUnit.MINUTES);
		LocalTime hasta = fin.truncatedTo(ChronoUnit.MINUTES);
		if (desde.equals(hasta)) {
			throw new IllegalArgumentException("La hora de inicio y la de término no pueden ser iguales; "
					+ "para todo el día, deja el horario vacío");
		}
		return new Horario(desde, hasta);
	}

	/** @return si el contacto está disponible a esa hora */
	public boolean cubre(LocalTime hora) {
		if (inicio.isBefore(fin)) {
			return !hora.isBefore(inicio) && hora.isBefore(fin);
		}
		return !hora.isBefore(inicio) || hora.isBefore(fin);
	}

}
