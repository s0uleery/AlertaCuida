package cl.ucn.alertacuida.domain.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Normalización y dígito verificador del RUN. */
class RunTest {

	/** Quita puntos y espacios, agrega el guion y pasa la k a mayúscula. */
	@Test
	void normalizaPuntosGuionYMinuscula() {
		assertThat(Run.de("12.345.678-5").valor()).isEqualTo("12345678-5");
		assertThat(Run.de("123456785").valor()).isEqualTo("12345678-5");
		assertThat(Run.de(" 10.000.013-k ").valor()).isEqualTo("10000013-K");
	}

	/** El mismo RUN escrito con o sin puntos es igual. */
	@Test
	void mismoRunConDistintoFormatoEsIgual() {
		assertThat(Run.de("11.111.111-1")).isEqualTo(Run.de("11111111-1"));
	}

	/** Calcula el dígito verificador, incluidos los casos 'K' y '0'. */
	@Test
	void calculaElDigitoVerificador() {
		assertThat(Run.digitoVerificador("12345678")).isEqualTo('5');
		assertThat(Run.digitoVerificador("10000013")).isEqualTo('K');
		assertThat(Run.digitoVerificador("11111111")).isEqualTo('1');
		assertThat(Run.digitoVerificador("1000030")).isEqualTo('0');
	}

	/** Rechaza un dígito verificador que no corresponde. */
	@Test
	void rechazaDigitoVerificadorIncorrecto() {
		assertThatThrownBy(() -> Run.de("12345678-9")).isInstanceOf(IllegalArgumentException.class);
	}

	/** Rechaza textos que no tienen forma de RUN. */
	@Test
	void rechazaFormatoInvalido() {
		assertThatThrownBy(() -> Run.de("abc")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Run.de("")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Run.de(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
