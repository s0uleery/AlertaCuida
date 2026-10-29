package cl.ucn.alertacuida.application.calendario;

import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;

/**
 * Horario hábil y feriados vigentes, para mostrarlos en el dashboard. Son de solo lectura: vienen
 * de calendario.yml y cambiarlos requiere reiniciar el backend.
 */
public class ConsultarCalendario {

	private final CalendarioHabil calendario;

	/** Recibe el calendario de calendario.yml; lo arma ConfiguracionCalendario. */
	public ConsultarCalendario(CalendarioHabil calendario) {
		this.calendario = calendario;
	}

	/** @return el horario de cada día hábil y los feriados, ordenados por fecha */
	public CalendarioHabil consultar() {
		return calendario;
	}

}
