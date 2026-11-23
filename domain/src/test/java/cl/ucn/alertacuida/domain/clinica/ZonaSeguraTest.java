package cl.ucn.alertacuida.domain.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Reglas de la zona segura de un paciente. */
class ZonaSeguraTest {

	/** Sin severidad, la zona es media; las coordenadas quedan con seis decimales y sin ceros sobrantes. */
	@Test
	void sinSeveridadEsMediaYLasCoordenadasSeLimpian() {
		ZonaSegura zona = ZonaSegura.de(new BigDecimal("-23.65090000"), new BigDecimal("-70.397512345"), 200, null);

		assertThat(zona.severidad()).isEqualTo(Severidad.MEDIA);
		assertThat(zona.latitud().toPlainString()).isEqualTo("-23.6509");
		assertThat(zona.longitud().toPlainString()).isEqualTo("-70.397512");
		ZonaSegura cientifica = ZonaSegura.de(new BigDecimal("1E+1"), BigDecimal.ZERO, 50, Severidad.GRAVE);
		assertThat(cientifica.latitud().toPlainString()).isEqualTo("10");
	}

	/** Dos zonas con las mismas coordenadas escritas distinto son iguales: así no se guarda una versión repetida. */
	@Test
	void lasMismasCoordenadasEscritasDistintoSonIguales() {
		assertThat(ZonaSegura.de(new BigDecimal("-23.6500"), new BigDecimal("-70.4"), 300, Severidad.MEDIA))
				.isEqualTo(ZonaSegura.de(new BigDecimal("-23.65"), new BigDecimal("-70.40"), 300, null));
	}

	/** El centro debe estar en el mapa y el radio entre 50 y 5000 metros. */
	@Test
	void rechazaCentroFueraDelMapaYRadioFueraDeRango() {
		assertThatThrownBy(() -> ZonaSegura.de(null, BigDecimal.ZERO, 200, null))
				.hasMessageContaining("centro");
		assertThatThrownBy(() -> ZonaSegura.de(new BigDecimal("-90.1"), BigDecimal.ZERO, 200, null))
				.hasMessageContaining("latitud");
		assertThatThrownBy(() -> ZonaSegura.de(BigDecimal.ZERO, new BigDecimal("180.5"), 200, null))
				.hasMessageContaining("longitud");
		assertThatThrownBy(() -> ZonaSegura.de(BigDecimal.ZERO, BigDecimal.ZERO, 49, null))
				.hasMessageContaining("radio");
		assertThatThrownBy(() -> ZonaSegura.de(BigDecimal.ZERO, BigDecimal.ZERO, 5001, null))
				.hasMessageContaining("radio");
		assertThat(ZonaSegura.de(new BigDecimal("90"), new BigDecimal("-180"), 5000, null).radioMetros())
				.isEqualTo(5000);
	}

}
