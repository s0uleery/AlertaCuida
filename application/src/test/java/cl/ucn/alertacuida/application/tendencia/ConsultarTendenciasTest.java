package cl.ucn.alertacuida.application.tendencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.tendencia.modelo.Agrupacion;
import cl.ucn.alertacuida.application.tendencia.modelo.MarcaAlerta;
import cl.ucn.alertacuida.application.tendencia.modelo.PuntoTendencia;
import cl.ucn.alertacuida.application.tendencia.modelo.TendenciasPaciente;
import cl.ucn.alertacuida.application.tendencia.modelo.UbicacionTendencia;
import cl.ucn.alertacuida.application.tendencia.puerto.Tendencias;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Rango y agrupación de las tendencias (decisión del 19-11-2026): días de Chile, por defecto los últimos 7;
 * hasta 2 días cada lectura, hasta 31 por hora, hasta 90 por día; más de 90 se rechaza.
 */
class ConsultarTendenciasTest {

	/** 19-11-2026 a las 10:00 de Chile (UTC-3). */
	private static final Instant AHORA = Instant.parse("2026-11-19T13:00:00Z");
	private static final UUID PACIENTE = UUID.randomUUID();

	private final PuertoFalso puerto = new PuertoFalso();
	private final ConsultarTendencias consultar = new ConsultarTendencias(puerto, Clock.fixed(AHORA, ZoneOffset.UTC));

	/** Sin fechas, del 13-11 al 19-11 de Chile, por hora. */
	@Test
	void porDefectoLosUltimosSieteDiasPorHora() {
		TendenciasPaciente tendencias = consultar.consultar(PACIENTE, null, null);

		assertThat(tendencias.agrupacion()).isEqualTo(Agrupacion.HORA);
		assertThat(tendencias.desde()).isEqualTo(Instant.parse("2026-11-13T03:00:00Z"));
		assertThat(tendencias.hasta()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
		assertThat(puerto.desde).isEqualTo(tendencias.desde());
		assertThat(puerto.hasta).isEqualTo(tendencias.hasta());
		assertThat(puerto.agrupacion).isEqualTo(Agrupacion.HORA);
		assertThat(puerto.agrupacionUbicaciones).isEqualTo(Agrupacion.HORA);
	}

	/** La agrupación depende de cuántos días tiene el rango, contando ambos extremos. */
	@ParameterizedTest
	@CsvSource({"1, LECTURA", "2, LECTURA", "3, HORA", "31, HORA", "32, DIA", "90, DIA"})
	void laAgrupacionDependeDelRango(int dias, Agrupacion esperada) {
		LocalDate hasta = LocalDate.of(2026, 11, 19);

		assertThat(consultar.consultar(PACIENTE, hasta.minusDays(dias - 1), hasta).agrupacion()).isEqualTo(esperada);
	}

	/** Más de 90 días, o desde posterior a hasta, se rechazan. */
	@Test
	void unRangoInvalidoSeRechaza() {
		LocalDate hoy = LocalDate.of(2026, 11, 19);
		assertThatThrownBy(() -> consultar.consultar(PACIENTE, hoy.minusDays(90), hoy))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> consultar.consultar(PACIENTE, hoy, hoy.minusDays(1)))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** Un paciente que no existe da 404. */
	@Test
	void unPacienteQueNoExiste() {
		puerto.existe = false;

		assertThatThrownBy(() -> consultar.consultar(PACIENTE, null, null)).isInstanceOf(NoEncontrado.class);
	}

	/** Puerto que recuerda lo pedido y no tiene lecturas. */
	private static final class PuertoFalso implements Tendencias {

		private boolean existe = true;
		private Instant desde;
		private Instant hasta;
		private Agrupacion agrupacion;
		private Agrupacion agrupacionUbicaciones;

		@Override
		public boolean existePaciente(UUID idPaciente) {
			return existe;
		}

		@Override
		public List<PuntoTendencia> puntos(UUID idPaciente, TipoLectura variable, Instant desde, Instant hasta,
				Agrupacion agrupacion) {
			this.desde = desde;
			this.hasta = hasta;
			this.agrupacion = agrupacion;
			return List.of();
		}

		@Override
		public List<MarcaAlerta> alertas(UUID idPaciente, Instant desde, Instant hasta) {
			return List.of();
		}

		@Override
		public List<UbicacionTendencia> ubicaciones(UUID idPaciente, Instant desde, Instant hasta,
				Agrupacion agrupacion) {
			agrupacionUbicaciones = agrupacion;
			return List.of();
		}

	}

}
