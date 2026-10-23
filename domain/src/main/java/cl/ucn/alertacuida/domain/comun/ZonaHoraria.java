package cl.ucn.alertacuida.domain.comun;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Zona horaria del programa. Todo se guarda en UTC, pero las fechas que ve el personal
 * ("hoy", fecha de baja, horario hábil) se evalúan en Chile.
 */
public final class ZonaHoraria {

	/** America/Santiago: considera el cambio de horario de verano. */
	public static final ZoneId CHILE = ZoneId.of("America/Santiago");

	private ZonaHoraria() {
	}

	/**
	 * @param reloj hora actual (en las pruebas, una fija)
	 * @return la fecha de hoy en Chile
	 */
	public static LocalDate hoy(Clock reloj) {
		return LocalDate.now(reloj.withZone(CHILE));
	}

}
