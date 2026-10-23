package cl.ucn.alertacuida.domain.personal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Política de contraseñas del personal (NIST: largo y lista de comunes). */
class PoliticaContrasenaTest {

	/** Una frase larga sirve aunque no tenga mayúsculas ni símbolos. */
	@Test
	void unaFraseLargaSirveSinMayusculasNiSimbolos() {
		assertThat(PoliticaContrasena.problema("caballo bateria grapa")).isEmpty();
	}

	/** Menos de 12 caracteres no sirve, y el mensaje dice el mínimo. */
	@Test
	void menosDeDoceCaracteresNoSirve() {
		assertThat(PoliticaContrasena.problema("Corta-1234!")).hasValueSatisfying(
				motivo -> assertThat(motivo).contains("12"));
		assertThat(PoliticaContrasena.problema(null)).isPresent();
	}

	/** Más de 72 bytes no sirve (la ñ ocupa dos bytes). */
	@Test
	void masDeSetentaYDosBytesNoSirve() {
		assertThat(PoliticaContrasena.problema("a".repeat(36) + "ñ".repeat(19))).isPresent();
	}

	/** Las contraseñas comunes no sirven, sin importar mayúsculas. */
	@Test
	void lasComunesNoSirvenSinImportarMayusculas() {
		assertThat(PoliticaContrasena.problema("PasswordPassword")).hasValue("La contraseña es demasiado común");
		assertThat(PoliticaContrasena.problema("123456789012")).isPresent();
	}

	/** Un solo carácter repetido no sirve. */
	@Test
	void unSoloCaracterRepetidoNoSirve() {
		assertThat(PoliticaContrasena.problema("zzzzzzzzzzzzzz")).isPresent();
	}

}
