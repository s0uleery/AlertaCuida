package cl.ucn.alertacuida.domain.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Reglas de un umbral: orden de los límites, SpO2 sin límite alto y rango del sensor. */
class UmbralTest {

	/** Un umbral de FC en orden se acepta, sin ceros sobrantes. */
	@Test
	void aceptaUnUmbralDeFcEnOrdenSinCerosSobrantes() {
		Umbral umbral = Umbral.de(TipoLectura.FC, numero("40.0"), numero("50"), numero("110"), numero("1.3E2"));

		assertThat(umbral.graveBajo().toPlainString()).isEqualTo("40");
		assertThat(umbral.graveAlto().toPlainString()).isEqualTo("130");
		assertThat(umbral).isEqualTo(Umbral.de(TipoLectura.FC, numero("40"), numero("50"), numero("110"),
				numero("130")));
	}

	/** Los límites deben ir en orden estricto. */
	@Test
	void rechazaLimitesDesordenadosOIguales() {
		assertThatThrownBy(() -> Umbral.de(TipoLectura.FC, numero("50"), numero("50"), numero("110"), numero("130")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Umbral.de(TipoLectura.FC, numero("40"), numero("120"), numero("110"), numero("130")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Umbral.de(TipoLectura.FC, numero("40"), numero("50"), numero("140"), numero("130")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** La SpO2 no lleva límites altos, y la FC los necesita. */
	@Test
	void laSpo2NoLlevaLimitesAltosYLaFcSi() {
		assertThat(Umbral.de(TipoLectura.SPO2, numero("88"), numero("92"), null, null).normalMax()).isNull();
		assertThatThrownBy(() -> Umbral.de(TipoLectura.SPO2, numero("88"), numero("92"), numero("99"), null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Umbral.de(TipoLectura.FC, numero("40"), numero("50"), null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** Un límite fuera de lo que mide el reloj (SpO2 sobre 100, FC bajo 20) se rechaza. */
	@Test
	void rechazaLimitesFueraDelRangoDelSensor() {
		assertThatThrownBy(() -> Umbral.de(TipoLectura.SPO2, numero("95"), numero("101"), null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Umbral.de(TipoLectura.FC, numero("10"), numero("50"), numero("110"), numero("130")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** @return el número escrito como texto */
	private static BigDecimal numero(String texto) {
		return new BigDecimal(texto);
	}

}
