package cl.ucn.alertacuida.adapters.web.calendario;

import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cuerpo de {@code GET /api/calendario}. Usa los mismos nombres que la configuración del reloj
 * (docs/api-reloj.yaml), y además el nombre de cada feriado para mostrarlo.
 *
 * @param zonaHoraria  siempre America/Santiago
 * @param horarioHabil un tramo por día hábil, de lunes a domingo; los días que no aparecen no son hábiles
 * @param feriados     ordenados por fecha
 */
public record RespuestaCalendario(String zonaHoraria, List<Dia> horarioHabil, List<Feriado> feriados) {

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	/**
	 * Horario de un día hábil.
	 *
	 * @param inicio "08:30", incluida
	 * @param fin    "17:30", sin incluirla
	 */
	public record Dia(DiaSemana dia, String inicio, String fin) {
	}

	/** Un feriado nacional. */
	public record Feriado(LocalDate fecha, String nombre) {
	}

	/** @return el calendario en el formato de la respuesta */
	static RespuestaCalendario de(CalendarioHabil calendario) {
		return new RespuestaCalendario(ZonaHoraria.CHILE.getId(),
				calendario.horario().entrySet().stream()
						.map(dia -> new Dia(dia.getKey(), hora(dia.getValue().inicio()), hora(dia.getValue().fin())))
						.toList(),
				calendario.feriados().stream().map(f -> new Feriado(f.fecha(), f.nombre())).toList());
	}

	private static String hora(LocalTime hora) {
		return hora.format(HORA);
	}

}
