package cl.ucn.alertacuida.application.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.reporte.modelo.IndicadoresPeriodo;
import cl.ucn.alertacuida.application.reporte.modelo.Periodo;
import cl.ucn.alertacuida.application.reporte.modelo.ReporteUsuario;
import cl.ucn.alertacuida.application.reporte.puerto.Reportes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Períodos de los reportes (decisiones del 19-11-2026): diario = un día de Chile; semanal = lunes a domingo de
 * Chile, elegido con cualquier día, comparado con la semana anterior. Se calculan al pedirlos.
 */
class ConsultarReportesTest {

	/** Jueves 19-11-2026 a las 10:00 de Chile (UTC-3). */
	private static final Instant AHORA = Instant.parse("2026-11-19T13:00:00Z");
	private static final UUID PACIENTE = UUID.randomUUID();

	private final PuertoFalso puerto = new PuertoFalso();
	private final ConsultarReportes consultar = new ConsultarReportes(puerto, Clock.fixed(AHORA, ZoneOffset.UTC));

	/** Diario sin día: hoy en Chile, de 00:00 a 00:00; sin comparación. */
	@Test
	void diarioPorDefectoHoy() {
		ReporteUsuario reporte = consultar.diario(PACIENTE, null);

		assertThat(reporte.periodo()).isEqualTo(Periodo.DIARIO);
		assertThat(reporte.desde()).isEqualTo(LocalDate.of(2026, 11, 19));
		assertThat(reporte.hasta()).isEqualTo(LocalDate.of(2026, 11, 19));
		assertThat(reporte.anterior()).isNull();
		assertThat(puerto.rangos).containsExactly(
				new Instant[] {Instant.parse("2026-11-19T03:00:00Z"), Instant.parse("2026-11-20T03:00:00Z")});
	}

	/** Semanal con un miércoles: de su lunes a su domingo, y la semana anterior para comparar. */
	@Test
	void semanalDeLunesADomingoConLaAnterior() {
		ReporteUsuario reporte = consultar.semanal(PACIENTE, LocalDate.of(2026, 11, 11));

		assertThat(reporte.periodo()).isEqualTo(Periodo.SEMANAL);
		assertThat(reporte.desde()).isEqualTo(LocalDate.of(2026, 11, 9));
		assertThat(reporte.hasta()).isEqualTo(LocalDate.of(2026, 11, 15));
		assertThat(reporte.anterior()).isNotNull();
		assertThat(puerto.rangos).containsExactly(
				new Instant[] {Instant.parse("2026-11-09T03:00:00Z"), Instant.parse("2026-11-16T03:00:00Z")},
				new Instant[] {Instant.parse("2026-11-02T03:00:00Z"), Instant.parse("2026-11-09T03:00:00Z")});
	}

	/** Semanal sin día: la semana de hoy. Un domingo cae en la semana que termina ese día. */
	@Test
	void semanalPorDefectoLaSemanaDeHoy() {
		assertThat(consultar.semanal(PACIENTE, null).desde()).isEqualTo(LocalDate.of(2026, 11, 16));
		assertThat(consultar.semanal(PACIENTE, LocalDate.of(2026, 11, 22)).desde())
				.isEqualTo(LocalDate.of(2026, 11, 16));
	}

	/** Un usuario que no existe da 404. */
	@Test
	void unUsuarioQueNoExiste() {
		puerto.existe = false;

		assertThatThrownBy(() -> consultar.diario(PACIENTE, null)).isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> consultar.semanal(PACIENTE, null)).isInstanceOf(NoEncontrado.class);
	}

	/** Puerto que recuerda cada rango pedido y devuelve indicadores vacíos. */
	private static final class PuertoFalso implements Reportes {

		private boolean existe = true;
		private final List<Instant[]> rangos = new ArrayList<>();

		@Override
		public boolean existePaciente(UUID idPaciente) {
			return existe;
		}

		@Override
		public IndicadoresPeriodo indicadores(UUID idPaciente, Instant desde, Instant hasta) {
			rangos.add(new Instant[] {desde, hasta});
			return new IndicadoresPeriodo(null, null, null, null, Map.of(), Map.of(), 0);
		}

	}

}
