package cl.ucn.alertacuida.domain.contacto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Normalización y validación del número de WhatsApp y del correo. */
class MediosDeContactoTest {

	/** Un celular chileno escrito de distintas formas queda igual. */
	@Test
	void normalizaUnCelularChilenoEscritoDeDistintasFormas() {
		assertThat(NumeroWhatsapp.de("9 1234 5678").valor()).isEqualTo("+56912345678");
		assertThat(NumeroWhatsapp.de("+56 9 1234-5678")).isEqualTo(NumeroWhatsapp.de("56912345678"));
		assertThat(NumeroWhatsapp.de("+54 9 11 2345 6789").valor()).isEqualTo("+5491123456789");
	}

	/** Los números de emergencia, como el SAMU, no pueden ser contactos. */
	@Test
	void rechazaLosNumerosDeEmergencia() {
		assertThatThrownBy(() -> NumeroWhatsapp.de("131"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("SAMU");
		assertThatThrownBy(() -> NumeroWhatsapp.de("+133")).isInstanceOf(IllegalArgumentException.class);
	}

	/** Un número fijo o incompleto se rechaza. */
	@Test
	void rechazaNumerosQueNoSonCelulares() {
		assertThatThrownBy(() -> NumeroWhatsapp.de("55 2123456")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> NumeroWhatsapp.de("912345")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> NumeroWhatsapp.de(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	/** El correo queda en minúsculas y sin espacios; uno sin dominio se rechaza. */
	@Test
	void normalizaElCorreoYRechazaUnoInvalido() {
		assertThat(Correo.de("  Ana.Rojas@Mail.CL ").valor()).isEqualTo("ana.rojas@mail.cl");
		assertThatThrownBy(() -> Correo.de("ana@mail")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Correo.de("ana rojas@mail.cl")).isInstanceOf(IllegalArgumentException.class);
	}

}
