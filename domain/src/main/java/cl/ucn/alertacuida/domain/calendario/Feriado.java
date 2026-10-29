package cl.ucn.alertacuida.domain.calendario;

import java.time.LocalDate;

/**
 * Un feriado nacional: el día completo deja de ser hábil.
 *
 * @param fecha  el día, en hora de Chile
 * @param nombre cómo se llama, para mostrarlo en el dashboard
 */
public record Feriado(LocalDate fecha, String nombre) {

	/** @throws IllegalArgumentException si falta la fecha o el nombre */
	public Feriado {
		if (fecha == null) {
			throw new IllegalArgumentException("El feriado necesita su fecha");
		}
		if (nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("El feriado del " + fecha + " necesita su nombre");
		}
		nombre = nombre.strip();
	}

}
