package cl.ucn.alertacuida.domain.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RunTest {

	@Test
	void normalizaPuntosGuionYMinuscula() {
		assertThat(Run.de("12.345.678-5").valor()).isEqualTo("12345678-5");
		assertThat(Run.de("123456785").valor()).isEqualTo("12345678-5");
		assertThat(Run.de(" 10.000.013-k ").valor()).isEqualTo("10000013-K");
	}

	@Test
	void mismoRunConDistintoFormatoEsIgual() {
		assertThat(Run.de("11.111.111-1")).isEqualTo(Run.de("11111111-1"));
	}

	@Test
	void calculaElDigitoVerificador() {
		assertThat(Run.digitoVerificador("12345678")).isEqualTo('5');
		assertThat(Run.digitoVerificador("10000013")).isEqualTo('K');
		assertThat(Run.digitoVerificador("11111111")).isEqualTo('1');
		assertThat(Run.digitoVerificador("1000030")).isEqualTo('0');
	}

	@Test
	void rechazaDigitoVerificadorIncorrecto() {
		assertThatThrownBy(() -> Run.de("12345678-9")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rechazaFormatoInvalido() {
		assertThatThrownBy(() -> Run.de("abc")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Run.de("")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Run.de(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
