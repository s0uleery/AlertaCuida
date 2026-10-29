package cl.ucn.alertacuida.domain.calendario;

import java.time.LocalTime;

/**
 * Horario hábil de un día, en hora de Chile. No cruza la medianoche.
 *
 * @param inicio desde esta hora, incluida (a las 08:30 en punto ya es hábil)
 * @param fin    hasta esta hora, sin incluirla (a las 17:30 en punto ya no es hábil)
 */
public record TramoHabil(LocalTime inicio, LocalTime fin) {

	/** @throws IllegalArgumentException si falta una hora o el inicio no es anterior al fin */
	public TramoHabil {
		if (inicio == null || fin == null) {
			throw new IllegalArgumentException("El horario hábil necesita la hora de inicio y la de término");
		}
		if (!inicio.isBefore(fin)) {
			throw new IllegalArgumentException("El horario hábil debe empezar antes de terminar: " + inicio + " a "
					+ fin);
		}
	}

	/** @return si esa hora está dentro del tramo */
	public boolean cubre(LocalTime hora) {
		return !hora.isBefore(inicio) && hora.isBefore(fin);
	}

}
