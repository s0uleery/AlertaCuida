package cl.ucn.alertacuida.application.escalamiento;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.escalamiento.modelo.AvisoOficina;
import cl.ucn.alertacuida.application.escalamiento.puerto.AvisosOficina;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Aviso a la oficina PRLAC, sin base de datos. Decisiones del 05-11, 08-11, 13-11 y 16-11-2026 (docs/alertas.md):
 * cada alerta se avisa a los coordinadores solo si ocurrió en horario laboral (fuera de él no se envía nada,
 * ni al empezar la jornada); si se agrava a grave después del aviso, se avisa de nuevo, solo si el
 * agravamiento ocurrió en horario laboral, y también si la alerta llegó fuera de horario y se agrava en él (sin
 * repetir si el aviso sale en la misma pasada). A quién y por qué canal lo decide la base (puerto
 * {@link AvisosOficina}); aquí se prueban el horario, la ventana y que una falla no detenga a los demás.
 */
class AvisarOficinaTest {

	/** Lunes hábil de 08:30 a 17:30. */
	private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
	private static final Duration VENTANA = Duration.ofHours(1);
	private static final CalendarioHabil CALENDARIO = CalendarioHabil.de(
			Map.of(DiaSemana.LUNES, new TramoHabil(LocalTime.of(8, 30), LocalTime.of(17, 30))), List.of());

	private final AvisosEnMemoria avisos = new AvisosEnMemoria();

	/** Una alerta recibida a mediodía se encola, y se piden los candidatos desde una ventana atrás. */
	@Test
	void unaAlertaEnHorarioSeEncola() {
		Instant ahora = chile(12, 0);
		AvisoOficina aviso = avisos.agregar(Proposito.AVISO, chile(11, 59));

		avisar(ahora);

		assertThat(avisos.encolados).containsExactly(aviso);
		assertThat(avisos.desdePedido).isEqualTo(ahora.minus(VENTANA));
	}

	/** Una alerta recibida antes de la jornada no se encola, tampoco al empezar la jornada. */
	@Test
	void fueraDeHorarioNoSeEncola() {
		avisos.agregar(Proposito.AVISO, chile(8, 0));

		avisar(chile(8, 10));
		avisar(chile(8, 40));

		assertThat(avisos.encolados).isEmpty();
	}

	/** El aviso de agravamiento se mide por la hora en que se agravó, no por la de la alerta. */
	@Test
	void elAgravamientoSeMidePorSuPropiaHora() {
		AvisoOficina enHorario = avisos.agregar(Proposito.AGRAVAMIENTO, chile(17, 0));
		avisos.agregar(Proposito.AGRAVAMIENTO, chile(17, 45));

		avisar(chile(17, 50));

		assertThat(avisos.encolados).containsExactly(enHorario);
	}

	/** Si falla encolar uno, se registra y se sigue con los demás. */
	@Test
	void unaFallaNoDetieneALosDemas() {
		AvisoOficina falla = avisos.agregar(Proposito.AVISO, chile(11, 0));
		AvisoOficina otro = avisos.agregar(Proposito.AVISO, chile(11, 30));
		avisos.queFalla = falla;

		avisar(chile(12, 0));

		assertThat(avisos.encolados).containsExactly(otro);
	}

	/**
	 * Si la alerta se agravó antes de que saliera su primer aviso, ese aviso ya dice grave: en la misma pasada no
	 * se encola también el agravamiento de esa alerta para esa cuenta.
	 */
	@Test
	void siElAvisoSaleEnLaMismaPasadaNoSeEncolaElAgravamiento() {
		AvisoOficina aviso = avisos.agregar(Proposito.AVISO, chile(11, 50));
		avisos.agravamientoDe(aviso, chile(11, 55));

		avisar(chile(12, 0));

		assertThat(avisos.encolados).containsExactly(aviso);
	}

	/** Si el aviso de la misma alerta no sale (llegó fuera de horario), el agravamiento en horario sí se encola. */
	@Test
	void siElAvisoNoSaleSeEncolaElAgravamiento() {
		AvisoOficina antesDeLaJornada = avisos.agregar(Proposito.AVISO, chile(8, 0));
		AvisoOficina agravamiento = avisos.agravamientoDe(antesDeLaJornada, chile(8, 40));

		avisar(chile(8, 45));

		assertThat(avisos.encolados).containsExactly(agravamiento);
	}

	/** Corre el aviso a la oficina con el reloj en ese momento. */
	private void avisar(Instant ahora) {
		Transacciones enTransaccion = Supplier::get;
		new AvisarOficina(avisos, CALENDARIO, VENTANA, enTransaccion, Clock.fixed(ahora, ZoneOffset.UTC)).avisar();
	}

	/** @return ese momento del lunes de prueba, en hora de Chile */
	private static Instant chile(int hora, int minuto) {
		return LUNES.atTime(hora, minuto).atZone(ZonaHoraria.CHILE).toInstant();
	}

	/** Avisos por encolar en memoria: la base ya los filtra por la ventana y por lo ya encolado. */
	private static final class AvisosEnMemoria implements AvisosOficina {

		private final List<AvisoOficina> candidatos = new ArrayList<>();
		private final List<AvisoOficina> encolados = new ArrayList<>();
		private Instant desdePedido;
		private AvisoOficina queFalla;

		AvisoOficina agregar(Proposito proposito, Instant momento) {
			AvisoOficina aviso = new AvisoOficina(UUID.randomUUID(), UUID.randomUUID(), proposito, Canal.WHATSAPP,
					momento);
			candidatos.add(aviso);
			return aviso;
		}

		AvisoOficina agravamientoDe(AvisoOficina aviso, Instant momento) {
			AvisoOficina agravamiento = new AvisoOficina(aviso.idAlerta(), aviso.idUsuario(),
					Proposito.AGRAVAMIENTO, aviso.canal(), momento);
			candidatos.add(agravamiento);
			return agravamiento;
		}

		@Override
		public List<AvisoOficina> porEncolar(Instant desde) {
			desdePedido = desde;
			return candidatos.stream().filter(a -> !encolados.contains(a)).toList();
		}

		@Override
		public void encolar(AvisoOficina aviso, Instant ahora) {
			if (aviso.equals(queFalla)) {
				throw new IllegalStateException("falla de prueba");
			}
			encolados.add(aviso);
		}

	}

}
