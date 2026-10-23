package cl.ucn.alertacuida.domain.personal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PoliticaContrasenaTest {

	@Test
	void unaFraseLargaSirveSinMayusculasNiSimbolos() {
		assertThat(PoliticaContrasena.problema("caballo bateria grapa")).isEmpty();
	}

	@Test
	void menosDeDoceCaracteresNoSirve() {
		assertThat(PoliticaContrasena.problema("Corta-1234!")).hasValueSatisfying(
				motivo -> assertThat(motivo).contains("12"));
		assertThat(PoliticaContrasena.problema(null)).isPresent();
	}

	@Test
	void masDeSetentaYDosBytesNoSirve() {
		assertThat(PoliticaContrasena.problema("a".repeat(36) + "ñ".repeat(19))).isPresent();
	}

	@Test
	void lasComunesNoSirvenSinImportarMayusculas() {
		assertThat(PoliticaContrasena.problema("PasswordPassword")).hasValue("La contraseña es demasiado común");
		assertThat(PoliticaContrasena.problema("123456789012")).isPresent();
	}

	@Test
	void unSoloCaracterRepetidoNoSirve() {
		assertThat(PoliticaContrasena.problema("zzzzzzzzzzzzzz")).isPresent();
	}

}
