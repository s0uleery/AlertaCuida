package cl.ucn.alertacuida.application.calendario;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.Persona;
import cl.ucn.alertacuida.application.contacto.modelo.Vinculo;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.Feriado;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regla de enrutamiento: la alerta leve fuera del horario hábil espera; las demás avisan a los
 * contactos disponibles a esa hora, por prioridad, respetando siempre el horario de cada contacto.
 */
class ReglaEnrutamientoTest {

	/** Martes 27-10-2026 a las 10:00, en horario hábil. */
	private static final Instant HABIL = enChile("2026-10-27T10:00");
	/** Martes 27-10-2026 a las 21:00, fuera del horario hábil. */
	private static final Instant NOCHE = enChile("2026-10-27T21:00");
	/** Lunes 12-10-2026 a las 10:00, feriado. */
	private static final Instant FERIADO = enChile("2026-10-12T10:00");

	private final ReglaEnrutamiento regla = new ReglaEnrutamiento(CalendarioHabil.de(
			Map.of(DiaSemana.LUNES, semana(), DiaSemana.MARTES, semana()),
			List.of(new Feriado(LocalDate.parse("2026-10-12"), "Encuentro de Dos Mundos"))));

	private final ContactoDelPaciente hija = contacto(1, null, true, true);
	private final ContactoDelPaciente vecinoNocturno = contacto(2, Horario.de(hora("18:00"), hora("08:00")), true,
			true);
	private final ContactoDelPaciente sobrinoDiurno = contacto(3, Horario.de(hora("09:00"), hora("18:00")), true,
			true);
	private final ContactoDelPaciente desactivado = contacto(4, null, false, true);
	private final ContactoDelPaciente sinCanal = contacto(5, null, true, false);
	private final List<ContactoDelPaciente> todos = List.of(sobrinoDiurno, desactivado, vecinoNocturno, sinCanal,
			hija);

	/** Una leve fuera de horario o en feriado espera y no avisa a nadie; en horario hábil, avisa. */
	@Test
	void laLeveFueraDeHorarioEspera() {
		assertThat(regla.esperaDiaHabil(Severidad.LEVE, NOCHE)).isTrue();
		assertThat(regla.esperaDiaHabil(Severidad.LEVE, FERIADO)).isTrue();
		assertThat(regla.contactosAAvisar(TipoEvento.CAIDA, Severidad.LEVE, NOCHE, todos)).isEmpty();
		assertThat(regla.esperaDiaHabil(Severidad.LEVE, HABIL)).isFalse();
	}

	/** Las medias y graves nunca esperan, sea la hora que sea. */
	@Test
	void lasMediasYGravesNuncaEsperan() {
		for (Instant momento : List.of(HABIL, NOCHE, FERIADO)) {
			assertThat(regla.esperaDiaHabil(Severidad.MEDIA, momento)).isFalse();
			assertThat(regla.esperaDiaHabil(Severidad.GRAVE, momento)).isFalse();
		}
	}

	/** En horario hábil se respeta el horario de cada contacto: el vecino nocturno no recibe el aviso de las 10:00. */
	@Test
	void enHorarioHabilSeRespetaElHorarioDeCadaContacto() {
		assertThat(regla.contactosAAvisar(TipoEvento.CAIDA, Severidad.LEVE, HABIL, todos))
				.containsExactly(hija, sobrinoDiurno);
	}

	/** De noche, una grave avisa solo a los disponibles a esa hora, por prioridad. */
	@Test
	void deNocheSeAvisaALosDisponiblesPorPrioridad() {
		assertThat(regla.contactosAAvisar(TipoEvento.CAIDA, Severidad.GRAVE, NOCHE, todos))
				.containsExactly(hija, vecinoNocturno);
	}

	/** Nunca se avisa a un contacto desactivado ni a uno sin canal usable (sin opt-in ni correo). */
	@Test
	void nuncaSeAvisaADesactivadosNiSinCanal() {
		assertThat(regla.contactosAAvisar(TipoEvento.CAIDA, Severidad.MEDIA, HABIL, List.of(desactivado, sinCanal)))
				.isEmpty();
	}

	/** Con el botón de ayuda avisan primero los vecinos y después el resto, cada grupo por prioridad. */
	@Test
	void elBotonDeAyudaAvisaPrimeroALosVecinos() {
		ContactoDelPaciente vecina = contacto(4, "VECINO");
		ContactoDelPaciente vecino = contacto(6, "VECINO");
		List<ContactoDelPaciente> lista = List.of(hija, vecino, sobrinoDiurno, vecina);

		assertThat(regla.contactosAAvisar(TipoEvento.BOTON_AYUDA, Severidad.LEVE, HABIL, lista))
				.containsExactly(vecina, vecino, hija, sobrinoDiurno);
		assertThat(regla.contactosAAvisar(TipoEvento.CAIDA, Severidad.LEVE, HABIL, lista))
				.containsExactly(hija, sobrinoDiurno, vecina, vecino);
	}

	/** El botón de ayuda es leve: de noche espera al día hábil, como cualquier leve. */
	@Test
	void elBotonDeAyudaDeNocheEspera() {
		assertThat(regla.contactosAAvisar(TipoEvento.BOTON_AYUDA, Severidad.LEVE, NOCHE,
				List.of(contacto(4, "VECINO"), hija))).isEmpty();
	}

	private static TramoHabil semana() {
		return new TramoHabil(hora("08:30"), hora("17:30"));
	}

	/**
	 * @param conCanal true: WhatsApp con opt-in vigente; false: sin WhatsApp ni correo
	 * @return un contacto con esa prioridad y ese horario
	 */
	private static ContactoDelPaciente contacto(int prioridad, Horario horario, boolean activo, boolean conCanal) {
		Persona persona = conCanal
				? new Persona(UUID.randomUUID(), "Contacto " + prioridad, "+56912345678",
						null, Instant.parse("2026-10-01T12:00:00Z"), null)
				: new Persona(UUID.randomUUID(), "Contacto " + prioridad, null, null, null, null);
		return new ContactoDelPaciente(UUID.randomUUID(), persona, new Vinculo("FAMILIA", null, false, horario),
				prioridad, activo, 0);
	}

	/** @return un contacto activo de ese tipo, sin horario y con WhatsApp */
	private static ContactoDelPaciente contacto(int prioridad, String tipo) {
		Persona persona = new Persona(UUID.randomUUID(), "Contacto " + prioridad, "+56912345678", null,
				Instant.parse("2026-10-01T12:00:00Z"), null);
		return new ContactoDelPaciente(UUID.randomUUID(), persona, new Vinculo(tipo, null, false, null), prioridad,
				true, 0);
	}

	private static LocalTime hora(String texto) {
		return LocalTime.parse(texto);
	}

	/** @return el instante de esa fecha y hora en Chile */
	private static Instant enChile(String fechaHora) {
		return LocalDateTime.parse(fechaHora).atZone(ZonaHoraria.CHILE).toInstant();
	}

}
