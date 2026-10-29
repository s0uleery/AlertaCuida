package cl.ucn.alertacuida.domain.calendario;

import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Horario hábil del PRLAC y feriados, en hora de Chile. Decide si una alerta leve avisa o espera al
 * día hábil. Viene de calendario.yml: cambiarlo requiere reiniciar el backend.
 *
 * <p>Un momento es hábil si su día tiene horario, no es feriado y la hora cae dentro del horario.
 * La hora se calcula en {@link ZonaHoraria#CHILE}, así que los cambios de hora de abril y
 * septiembre no corren el horario.
 */
public final class CalendarioHabil {

	private final Map<DiaSemana, TramoHabil> horario;
	private final List<Feriado> feriados;
	private final Set<LocalDate> fechasFeriado;

	private CalendarioHabil(Map<DiaSemana, TramoHabil> horario, List<Feriado> feriados) {
		this.horario = Collections.unmodifiableMap(new EnumMap<>(horario));
		this.feriados = feriados.stream().sorted(Comparator.comparing(Feriado::fecha)).toList();
		this.fechasFeriado = Set.copyOf(feriados.stream().map(Feriado::fecha).toList());
	}

	/**
	 * @param horario  tramo de cada día hábil; los días que no aparecen no son hábiles
	 * @param feriados feriados nacionales, en cualquier orden
	 * @return el calendario, con los feriados ordenados por fecha
	 * @throws IllegalArgumentException si no hay ningún día hábil o se repite la fecha de un feriado
	 */
	public static CalendarioHabil de(Map<DiaSemana, TramoHabil> horario, List<Feriado> feriados) {
		if (horario.isEmpty()) {
			throw new IllegalArgumentException("El calendario necesita al menos un día hábil");
		}
		Set<LocalDate> vistas = new HashSet<>();
		for (Feriado feriado : feriados) {
			if (!vistas.add(feriado.fecha())) {
				throw new IllegalArgumentException("El feriado del " + feriado.fecha() + " está repetido");
			}
		}
		return new CalendarioHabil(horario, feriados);
	}

	/** @return si ese momento está dentro del horario hábil y no es feriado */
	public boolean esHabil(Instant momento) {
		ZonedDateTime enChile = momento.atZone(ZonaHoraria.CHILE);
		if (esFeriado(enChile.toLocalDate())) {
			return false;
		}
		TramoHabil tramo = horario.get(DiaSemana.de(enChile.getDayOfWeek()));
		return tramo != null && tramo.cubre(enChile.toLocalTime());
	}

	/** @return si ese día es feriado */
	public boolean esFeriado(LocalDate dia) {
		return fechasFeriado.contains(dia);
	}

	/** @return el tramo de cada día hábil, de lunes a domingo */
	public Map<DiaSemana, TramoHabil> horario() {
		return horario;
	}

	/** @return los feriados, ordenados por fecha */
	public List<Feriado> feriados() {
		return feriados;
	}

}
