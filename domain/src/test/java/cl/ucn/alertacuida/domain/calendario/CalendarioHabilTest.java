package cl.ucn.alertacuida.domain.calendario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Horario hábil y feriados: los límites del horario, el viernes más corto, los fines de semana, los
 * feriados y los días de cambio de hora. El horario es el del PRLAC: lunes a jueves de 08:30 a
 * 17:30 y viernes de 08:30 a 16:30.
 */
class CalendarioHabilTest {

	private static final TramoHabil SEMANA = new TramoHabil(hora("08:30"), hora("17:30"));
	private static final TramoHabil VIERNES = new TramoHabil(hora("08:30"), hora("16:30"));

	private final CalendarioHabil calendario = CalendarioHabil.de(
			Map.of(DiaSemana.LUNES, SEMANA, DiaSemana.MARTES, SEMANA, DiaSemana.MIERCOLES, SEMANA,
					DiaSemana.JUEVES, SEMANA, DiaSemana.VIERNES, VIERNES),
			List.of(new Feriado(LocalDate.parse("2026-10-12"), "Encuentro de Dos Mundos"),
					new Feriado(LocalDate.parse("2026-04-03"), "Viernes Santo")));

	/** A las 08:30 en punto ya es hábil, a las 17:29 todavía y a las 17:30 ya no. */
	@Test
	void elHorarioIncluyeSuInicioYNoSuTermino() {
		assertThat(calendario.esHabil(enChile("2026-10-27T08:29"))).isFalse();
		assertThat(calendario.esHabil(enChile("2026-10-27T08:30"))).isTrue();
		assertThat(calendario.esHabil(enChile("2026-10-27T17:29"))).isTrue();
		assertThat(calendario.esHabil(enChile("2026-10-27T17:30"))).isFalse();
	}

	/** El viernes termina a las 16:30. */
	@Test
	void elViernesTerminaAntes() {
		assertThat(calendario.esHabil(enChile("2026-10-30T16:29"))).isTrue();
		assertThat(calendario.esHabil(enChile("2026-10-30T16:30"))).isFalse();
	}

	/** Sábado y domingo no tienen horario: nunca son hábiles. */
	@Test
	void elFinDeSemanaNoEsHabil() {
		assertThat(calendario.esHabil(enChile("2026-10-31T10:00"))).isFalse();
		assertThat(calendario.esHabil(enChile("2026-11-01T10:00"))).isFalse();
	}

	/** Un feriado en día de semana no es hábil a ninguna hora; el día siguiente sí. */
	@Test
	void unFeriadoNoEsHabil() {
		assertThat(calendario.esFeriado(LocalDate.parse("2026-10-12"))).isTrue();
		assertThat(calendario.esHabil(enChile("2026-10-12T10:00"))).isFalse();
		assertThat(calendario.esHabil(enChile("2026-10-13T10:00"))).isTrue();
	}

	/**
	 * En abril Chile atrasa la hora (de UTC-3 a UTC-4, la noche del 4 al 5 de abril de 2026): las
	 * 08:30 de Chile son las 11:30 UTC antes del cambio y las 12:30 UTC después.
	 */
	@Test
	void elCambioDeHoraDeAbrilNoCorreElHorario() {
		assertThat(calendario.esHabil(Instant.parse("2026-03-30T11:29:00Z"))).isFalse();
		assertThat(calendario.esHabil(Instant.parse("2026-03-30T11:30:00Z"))).isTrue();
		assertThat(calendario.esHabil(Instant.parse("2026-04-06T12:29:00Z"))).isFalse();
		assertThat(calendario.esHabil(Instant.parse("2026-04-06T12:30:00Z"))).isTrue();
	}

	/**
	 * En septiembre Chile adelanta la hora (de UTC-4 a UTC-3, la noche del 5 al 6 de septiembre de
	 * 2026): las 08:30 de Chile son las 12:30 UTC antes del cambio y las 11:30 UTC después.
	 */
	@Test
	void elCambioDeHoraDeSeptiembreNoCorreElHorario() {
		assertThat(calendario.esHabil(Instant.parse("2026-09-04T12:29:00Z"))).isFalse();
		assertThat(calendario.esHabil(Instant.parse("2026-09-04T12:30:00Z"))).isTrue();
		assertThat(calendario.esHabil(Instant.parse("2026-09-07T11:29:00Z"))).isFalse();
		assertThat(calendario.esHabil(Instant.parse("2026-09-07T11:30:00Z"))).isTrue();
	}

	/** Los feriados quedan ordenados por fecha, aunque lleguen desordenados. */
	@Test
	void losFeriadosQuedanOrdenados() {
		assertThat(calendario.feriados()).extracting(Feriado::nombre)
				.containsExactly("Viernes Santo", "Encuentro de Dos Mundos");
	}

	/** Se rechazan un tramo al revés, un calendario sin días hábiles y un feriado repetido o sin nombre. */
	@Test
	void seRechazanLosDatosInvalidos() {
		assertThatThrownBy(() -> new TramoHabil(hora("17:30"), hora("08:30")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TramoHabil(hora("08:30"), hora("08:30")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CalendarioHabil.de(Map.of(), List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		LocalDate navidad = LocalDate.parse("2026-12-25");
		assertThatThrownBy(() -> CalendarioHabil.de(Map.of(DiaSemana.LUNES, SEMANA),
				List.of(new Feriado(navidad, "Navidad"), new Feriado(navidad, "Navidad"))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Feriado(navidad, " ")).isInstanceOf(IllegalArgumentException.class);
	}

	/** Los días en español corresponden a los de Java. */
	@Test
	void losDiasCorrespondenALosDeJava() {
		for (DiaSemana dia : DiaSemana.values()) {
			assertThat(DiaSemana.de(dia.dayOfWeek())).isEqualTo(dia);
		}
	}

	private static LocalTime hora(String texto) {
		return LocalTime.parse(texto);
	}

	/** @return el instante de esa fecha y hora en Chile (ej. "2026-10-27T08:30") */
	private static Instant enChile(String fechaHora) {
		return LocalDateTime.parse(fechaHora).atZone(ZonaHoraria.CHILE).toInstant();
	}

}
