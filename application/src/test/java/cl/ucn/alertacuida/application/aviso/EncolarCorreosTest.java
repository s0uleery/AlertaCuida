package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.modelo.AvisoPorEncolar;
import cl.ucn.alertacuida.application.aviso.puerto.ColaCorreos;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Qué correos se encolan, sin base de datos. Requisitos (decisiones del 05-11 y 08-11-2026):
 * <ul>
 * <li>Las cuentas con recibe_avisos reciben correo solo por las alertas técnicas (las de salud las
 * enruta el reloj), y solo en horario laboral; fuera de él no se envía nada, ni siquiera al empezar la
 * jornada.</li>
 * <li>Si un WhatsApp falla, se envía un correo de respaldo (a cualquier hora).</li>
 * </ul>
 */
class EncolarCorreosTest {

	/** Lunes hábil de 08:30 a 17:30. */
	private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
	private static final Instant MEDIODIA = chile(12, 0);
	private static final Duration VENTANA = Duration.ofHours(1);
	private static final CalendarioHabil CALENDARIO = CalendarioHabil.de(
			Map.of(DiaSemana.LUNES, new TramoHabil(LocalTime.of(8, 30), LocalTime.of(17, 30))), List.of());

	private final ColaEnMemoria cola = new ColaEnMemoria();

	/** Una alerta del horario hábil se encola para la cuenta. */
	@Test
	void enHorarioHabilSeEncola() {
		AvisoPorEncolar aviso = cola.porEncolar(MEDIODIA.minus(Duration.ofMinutes(2)));

		encolar(MEDIODIA);

		assertThat(cola.encolados).containsExactly(aviso);
	}

	/** Una alerta de fuera del horario no se encola, aunque la revisión corra ya en horario hábil. */
	@Test
	void fueraDelHorarioNoSeEncolaNiAlEmpezarLaJornada() {
		cola.porEncolar(chile(8, 25));

		encolar(chile(8, 35));

		assertThat(cola.encolados).isEmpty();
	}

	/** El fin del horario no cuenta: a las 17:30 en punto ya es fuera de horario. */
	@Test
	void alFinDelHorarioYaNoSeEncola() {
		cola.porEncolar(chile(17, 30));

		encolar(chile(17, 31));

		assertThat(cola.encolados).isEmpty();
	}

	/** Busca alertas solo dentro de la ventana, para no avisar de alertas viejas. */
	@Test
	void buscaSoloDentroDeLaVentana() {
		encolar(MEDIODIA);

		assertThat(cola.desdeConsultado).isEqualTo(MEDIODIA.minus(VENTANA));
	}

	/**
	 * recibe_avisos es solo para las técnicas: las de salud las enruta el reloj a los contactos del
	 * paciente (decisión del 10-11-2026).
	 */
	@Test
	void recibeAvisosEsSoloParaLasTecnicas() {
		encolar(MEDIODIA);

		assertThat(cola.tiposConsultados).containsExactlyInAnyOrderElementsOf(TipoEvento.tecnicas())
				.doesNotContain(TipoEvento.CAIDA, TipoEvento.FC, TipoEvento.BOTON_AYUDA);
	}
	/** Los respaldos de los WhatsApp fallidos se encolan con la misma ventana, a cualquier hora. */
	@Test
	void encolaLosRespaldosDeNoche() {
		Instant noche = chile(23, 0);

		encolar(noche);

		assertThat(cola.respaldos).containsExactly(noche.minus(VENTANA));
	}

	/** Si falla una cuenta, se sigue con las demás. */
	@Test
	void unFalloNoDetieneALasDemasCuentas() {
		AvisoPorEncolar roto = cola.porEncolar(MEDIODIA);
		AvisoPorEncolar bueno = cola.porEncolar(MEDIODIA);
		cola.rotos.add(roto);

		encolar(MEDIODIA);

		assertThat(cola.encolados).containsExactly(bueno);
		assertThat(cola.respaldos).hasSize(1);
	}

	/** Corre el caso de uso a esa hora. */
	private void encolar(Instant ahora) {
		Transacciones enTransaccion = Supplier::get;
		new EncolarCorreos(cola, CALENDARIO, VENTANA, enTransaccion, Clock.fixed(ahora, ZoneOffset.UTC)).encolar();
	}

	/** @return ese momento del lunes de las pruebas, hora de Chile */
	private static Instant chile(int hora, int minuto) {
		return LUNES.atTime(hora, minuto).atZone(ZonaHoraria.CHILE).toInstant();
	}

	/** Cola en memoria: guarda lo que el caso de uso encola. */
	private static final class ColaEnMemoria implements ColaCorreos {

		private final List<AvisoPorEncolar> candidatos = new ArrayList<>();
		private final List<AvisoPorEncolar> encolados = new ArrayList<>();
		private final List<AvisoPorEncolar> rotos = new ArrayList<>();
		private final List<Instant> respaldos = new ArrayList<>();
		private Instant desdeConsultado;
		private Set<TipoEvento> tiposConsultados;

		/** @return un aviso de una alerta de ese momento a una cuenta, aún sin encolar */
		AvisoPorEncolar porEncolar(Instant momento) {
			AvisoPorEncolar aviso = new AvisoPorEncolar(UUID.randomUUID(), UUID.randomUUID(), momento);
			candidatos.add(aviso);
			return aviso;
		}

		@Override
		public List<AvisoPorEncolar> avisosAlEquipoPorEncolar(Set<TipoEvento> tipos, Instant desde) {
			desdeConsultado = desde;
			tiposConsultados = tipos;
			return List.copyOf(candidatos);
		}

		@Override
		public void encolarParaCuenta(UUID idAlerta, UUID idUsuario, Instant ahora) {
			AvisoPorEncolar aviso = candidatos.stream()
					.filter(c -> c.idAlerta().equals(idAlerta) && c.idUsuario().equals(idUsuario))
					.findFirst().orElseThrow();
			if (rotos.contains(aviso)) {
				throw new IllegalStateException("falla de la base");
			}
			encolados.add(aviso);
		}

		@Override
		public int encolarRespaldos(Instant desde, Instant ahora) {
			respaldos.add(desde);
			return 0;
		}

	}

}
