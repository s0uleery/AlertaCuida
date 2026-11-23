package cl.ucn.alertacuida.application.redseguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.clinica.modelo.ValoresBase;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.redseguridad.modelo.CriterioHallazgos;
import cl.ucn.alertacuida.application.redseguridad.modelo.Hallazgo;
import cl.ucn.alertacuida.application.redseguridad.puerto.Hallazgos;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rango y criterio de la red de seguridad (decisiones del 19-11-2026): días de Chile, por defecto los últimos
 * 7, a lo más 31; ventana de 30 minutos y los umbrales base de application.properties.
 */
class ConsultarHallazgosTest {

	/** 19-11-2026 a las 10:00 de Chile (UTC-3). */
	private static final Instant AHORA = Instant.parse("2026-11-19T13:00:00Z");

	private static final Umbral FC = Umbral.de(TipoLectura.FC, new BigDecimal(40), new BigDecimal(50),
			new BigDecimal(110), new BigDecimal(130));
	private static final Umbral SPO2 = Umbral.de(TipoLectura.SPO2, new BigDecimal(88), new BigDecimal(92), null,
			null);

	private final PuertoFalso puerto = new PuertoFalso();
	private final ConsultarHallazgos consultar = new ConsultarHallazgos(puerto, new ValoresBase(FC, SPO2, 5),
			Clock.fixed(AHORA, ZoneOffset.UTC));

	/** Sin fechas, del 13-11 al 19-11 de Chile, ambos incluidos. */
	@Test
	void porDefectoLosUltimosSieteDias() {
		consultar.listar(null, null);

		assertThat(puerto.criterio.desde()).isEqualTo(Instant.parse("2026-11-13T03:00:00Z"));
		assertThat(puerto.criterio.hasta()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
	}

	/** Solo desde: hasta hoy. Solo hasta: los 7 días que terminan ese día. */
	@Test
	void unaSolaFechaCompletaLaOtra() {
		consultar.listar(LocalDate.of(2026, 11, 1), null);
		assertThat(puerto.criterio.desde()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
		assertThat(puerto.criterio.hasta()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));

		consultar.listar(null, LocalDate.of(2026, 11, 10));
		assertThat(puerto.criterio.desde()).isEqualTo(Instant.parse("2026-11-04T03:00:00Z"));
		assertThat(puerto.criterio.hasta()).isEqualTo(Instant.parse("2026-11-11T03:00:00Z"));
	}

	/** El criterio lleva la ventana de 30 minutos y los umbrales base. */
	@Test
	void elCriterioLlevaLaVentanaYLosUmbralesBase() {
		consultar.listar(null, null);

		assertThat(puerto.criterio.ventana()).isEqualTo(Duration.ofMinutes(30));
		assertThat(puerto.criterio.baseFc()).isEqualTo(FC);
		assertThat(puerto.criterio.baseSpo2()).isEqualTo(SPO2);
	}

	/** Desde posterior a hasta se rechaza. */
	@Test
	void desdeDespuesDeHastaSeRechaza() {
		assertThatThrownBy(() -> consultar.listar(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 18)))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** 31 días se aceptan; 32, no. */
	@Test
	void aLoMasTreintaYUnDias() {
		consultar.listar(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19));
		assertThatThrownBy(() -> consultar.listar(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 19)))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** Puerto que guarda el último criterio y no devuelve hallazgos. */
	private static final class PuertoFalso implements Hallazgos {

		private CriterioHallazgos criterio;

		@Override
		public List<Hallazgo> buscar(CriterioHallazgos criterio) {
			this.criterio = criterio;
			return List.of();
		}

	}

}
