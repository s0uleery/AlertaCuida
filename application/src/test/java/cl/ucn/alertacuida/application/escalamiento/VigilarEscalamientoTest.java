package cl.ucn.alertacuida.application.escalamiento;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.escalamiento.modelo.AlertaTrabada;
import cl.ucn.alertacuida.application.escalamiento.puerto.EscalamientosTrabados;
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
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Vigilante del escalamiento, sin base de datos. Decisiones del 13-11-2026 (docs/alertas.md): si una alerta
 * del reloj sigue abierta y el reloj lleva 15 minutos sin pedir avisos, se avisa por correo a todos los
 * coordinadores activos, una sola vez por alerta y solo si esos 15 minutos se cumplieron en horario laboral
 * (fuera de él no se envía nada, ni al empezar la jornada). Qué alertas están trabadas lo decide la base
 * (puerto {@link EscalamientosTrabados}); aquí se prueba el plazo, el horario y la ventana.
 */
class VigilarEscalamientoTest {

	/** Lunes hábil de 08:30 a 17:30. */
	private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
	private static final Duration PLAZO = Duration.ofMinutes(15);
	private static final Duration VENTANA = Duration.ofHours(1);
	private static final CalendarioHabil CALENDARIO = CalendarioHabil.de(
			Map.of(DiaSemana.LUNES, new TramoHabil(LocalTime.of(8, 30), LocalTime.of(17, 30))), List.of());

	private final TrabadosEnMemoria trabados = new TrabadosEnMemoria();

	/** Una alerta sin noticias del reloj hace 20 minutos, a mediodía, se avisa a los coordinadores. */
	@Test
	void sinNoticiasDuranteElPlazoSeAvisa() {
		Instant ahora = chile(12, 0);
		UUID alerta = trabados.agregar(ahora.minus(Duration.ofMinutes(20)));

		vigilar(ahora);

		assertThat(trabados.avisadas).containsExactly(alerta);
		assertThat(trabados.limitePedido).isEqualTo(ahora.minus(PLAZO));
	}

	/** Si el plazo se cumplió fuera del horario laboral, no se avisa (tampoco al empezar la jornada). */
	@Test
	void fueraDeHorarioNoSeAvisa() {
		trabados.agregar(chile(7, 0));

		vigilar(chile(8, 0));
		vigilar(chile(8, 40));

		assertThat(trabados.avisadas).isEmpty();
	}

	/** Un plazo cumplido hace más que la ventana no se avisa: la alerta ya es vieja (servidor detenido, etc.). */
	@Test
	void unPlazoVencidoHaceMasDeUnaHoraNoSeAvisa() {
		Instant ahora = chile(15, 0);
		trabados.agregar(ahora.minus(PLAZO).minus(VENTANA).minusSeconds(1));
		UUID reciente = trabados.agregar(ahora.minus(PLAZO).minus(VENTANA));

		vigilar(ahora);

		assertThat(trabados.avisadas).containsExactly(reciente);
	}

	/** Si falla el aviso de una alerta, se registra y se sigue con las demás. */
	@Test
	void unaFallaNoDetieneALasDemas() {
		Instant ahora = chile(12, 0);
		UUID falla = trabados.agregar(ahora.minus(Duration.ofMinutes(30)));
		UUID otra = trabados.agregar(ahora.minus(Duration.ofMinutes(20)));
		trabados.queFalla = falla;

		vigilar(ahora);

		assertThat(trabados.avisadas).containsExactly(otra);
	}

	/** Corre el vigilante con el reloj en ese momento. */
	private void vigilar(Instant ahora) {
		Transacciones enTransaccion = Supplier::get;
		new VigilarEscalamiento(trabados, CALENDARIO, PLAZO, VENTANA, enTransaccion,
				Clock.fixed(ahora, ZoneOffset.UTC)).vigilar();
	}

	/** @return ese momento del lunes de prueba, en hora de Chile */
	private static Instant chile(int hora, int minuto) {
		return LUNES.atTime(hora, minuto).atZone(ZonaHoraria.CHILE).toInstant();
	}

	/** Alertas trabadas en memoria: la base ya las filtra por el límite pedido. */
	private static final class TrabadosEnMemoria implements EscalamientosTrabados {

		private final List<AlertaTrabada> todas = new ArrayList<>();
		private final List<UUID> avisadas = new ArrayList<>();
		private Instant limitePedido;
		private UUID queFalla;

		UUID agregar(Instant ultimaNoticia) {
			UUID id = UUID.randomUUID();
			todas.add(new AlertaTrabada(id, ultimaNoticia));
			return id;
		}

		@Override
		public List<AlertaTrabada> trabadas(Instant limite) {
			limitePedido = limite;
			return todas.stream().filter(a -> !a.ultimaNoticia().isAfter(limite)).toList();
		}

		@Override
		public void avisarCoordinadores(UUID idAlerta, Instant ahora) {
			if (idAlerta.equals(queFalla)) {
				throw new IllegalStateException("falla de prueba");
			}
			avisadas.add(idAlerta);
		}

	}

}
