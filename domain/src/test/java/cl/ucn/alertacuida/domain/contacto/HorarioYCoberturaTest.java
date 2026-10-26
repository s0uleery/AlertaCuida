package cl.ucn.alertacuida.domain.contacto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Horarios de los contactos (incluidos los que cruzan la medianoche) y las horas sin cobertura. */
class HorarioYCoberturaTest {

	/** Un horario diurno incluye su inicio y no su término. */
	@Test
	void unHorarioIncluyeSuInicioYNoSuTermino() {
		Horario diurno = Horario.de(hora("08:00"), hora("20:00"));

		assertThat(diurno.cubre(hora("08:00"))).isTrue();
		assertThat(diurno.cubre(hora("19:59"))).isTrue();
		assertThat(diurno.cubre(hora("20:00"))).isFalse();
	}

	/** Si el inicio es posterior al término, el horario cruza la medianoche. */
	@Test
	void unHorarioNocturnoCruzaLaMedianoche() {
		Horario nocturno = Horario.de(hora("22:00"), hora("07:00"));

		assertThat(nocturno.cubre(hora("23:30"))).isTrue();
		assertThat(nocturno.cubre(hora("00:00"))).isTrue();
		assertThat(nocturno.cubre(hora("07:00"))).isFalse();
		assertThat(nocturno.cubre(hora("12:00"))).isFalse();
	}

	/** Sin horas es todo el día; con una sola hora o dos iguales se rechaza. */
	@Test
	void sinHorasEsTodoElDiaYUnaSolaOIgualesSeRechaza() {
		assertThat(Horario.de(null, null)).isNull();
		assertThatThrownBy(() -> Horario.de(hora("08:00"), null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Horario.de(hora("08:00"), hora("08:00")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** Sin contactos disponibles, falta el día completo; con uno de todo el día, no falta nada. */
	@Test
	void sinContactosFaltaElDiaCompletoYConUnoDeTodoElDiaNada() {
		assertThat(Cobertura.sinCubrir(List.of()))
				.containsExactly(new Cobertura.Tramo(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT));
		assertThat(Cobertura.sinCubrir(Arrays.asList((Horario) null))).isEmpty();
	}

	/** Un contacto de 08:00 a 20:00 deja sin cobertura la noche, como un solo tramo que cruza la medianoche. */
	@Test
	void elHuecoNocturnoEsUnSoloTramo() {
		assertThat(Cobertura.sinCubrir(List.of(Horario.de(hora("08:00"), hora("20:00")))))
				.containsExactly(new Cobertura.Tramo(hora("20:00"), hora("08:00")));
	}

	/** Un contacto diurno y otro nocturno se completan; si no se tocan, queda el hueco entre ellos. */
	@Test
	void dosHorariosSeCompletanODejanUnHueco() {
		Horario diurno = Horario.de(hora("08:00"), hora("20:00"));

		assertThat(Cobertura.sinCubrir(List.of(diurno, Horario.de(hora("20:00"), hora("08:00"))))).isEmpty();
		assertThat(Cobertura.sinCubrir(List.of(diurno, Horario.de(hora("21:00"), hora("07:30")))))
				.containsExactly(new Cobertura.Tramo(hora("07:30"), hora("08:00")),
						new Cobertura.Tramo(hora("20:00"), hora("21:00")));
	}

	/** @return la hora escrita como "HH:mm" */
	private static LocalTime hora(String texto) {
		return LocalTime.parse(texto);
	}

}
