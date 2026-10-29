package cl.ucn.alertacuida.domain.calendario;

import java.time.DayOfWeek;

/** Día de la semana con el nombre que usan la configuración, el dashboard y el contrato del reloj. */
public enum DiaSemana {
	LUNES(DayOfWeek.MONDAY),
	MARTES(DayOfWeek.TUESDAY),
	MIERCOLES(DayOfWeek.WEDNESDAY),
	JUEVES(DayOfWeek.THURSDAY),
	VIERNES(DayOfWeek.FRIDAY),
	SABADO(DayOfWeek.SATURDAY),
	DOMINGO(DayOfWeek.SUNDAY);

	private final DayOfWeek dia;

	DiaSemana(DayOfWeek dia) {
		this.dia = dia;
	}

	/** @return el día de la semana de Java que corresponde a este */
	public DayOfWeek dayOfWeek() {
		return dia;
	}

	/** @return el día con nombre en español que corresponde al de Java */
	public static DiaSemana de(DayOfWeek dia) {
		return values()[dia.getValue() - 1];
	}

}
