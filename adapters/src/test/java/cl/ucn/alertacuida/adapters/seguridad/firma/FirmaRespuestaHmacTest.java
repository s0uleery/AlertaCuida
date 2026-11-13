package cl.ucn.alertacuida.adapters.seguridad.firma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Token de los enlaces de respuesta del correo al contacto (decisión del 12-11-2026: firmado por el
 * servidor, sin guardarlo en la base):
 * <ul>
 * <li>R1 El servidor reconoce el aviso de un token que él firmó.</li>
 * <li>R2 Un token alterado, firmado con otra clave, mal formado o vacío no sirve.</li>
 * <li>R3 La clave tiene al menos 32 caracteres: con una más corta el backend no arranca.</li>
 * <li>R4 El token cabe en una dirección sin escaparlo (solo letras, números, "-", "_" y ".").</li>
 * </ul>
 */
class FirmaRespuestaHmacTest {

	private static final String CLAVE = "clave-de-respuesta-de-las-pruebas-123";

	private final FirmaRespuestaHmac firma = new FirmaRespuestaHmac(CLAVE);

	/** R1: lo que se firma se reconoce. */
	@Test
	void reconoceLoQueFirmo() {
		UUID aviso = UUID.randomUUID();

		assertThat(firma.verificar(firma.firmar(aviso))).contains(aviso);
	}

	/**
	 * R2: cambiar un solo carácter de la firma la invalida. Se cambia el primero: el último de un base64
	 * lleva bits de relleno.
	 */
	@Test
	void tokenAlteradoNoSirve() {
		String token = firma.firmar(UUID.randomUUID());
		int inicio = token.indexOf('.') + 1;
		char primero = token.charAt(inicio);
		String alterado = token.substring(0, inicio) + (primero == 'A' ? 'B' : 'A') + token.substring(inicio + 1);

		assertThat(firma.verificar(alterado)).isEmpty();
	}

	/** R2: cambiar el aviso sin volver a firmar no sirve. */
	@Test
	void otroAvisoConLaMismaFirmaNoSirve() {
		String token = firma.firmar(UUID.randomUUID());
		String otro = firma.firmar(UUID.randomUUID());
		String mezclado = otro.substring(0, otro.indexOf('.')) + token.substring(token.indexOf('.'));

		assertThat(firma.verificar(mezclado)).isEmpty();
	}

	/** R2: un token firmado con otra clave no sirve. */
	@Test
	void otraClaveNoSirve() {
		String token = new FirmaRespuestaHmac("otra-clave-de-respuesta-de-pruebas-456").firmar(UUID.randomUUID());

		assertThat(firma.verificar(token)).isEmpty();
	}

	/** R2: textos que no son un token no sirven, sin lanzar errores. */
	@Test
	void basuraNoSirve() {
		for (String basura : new String[] {null, "", ".", "abc", "abc.def", "a.b.c", "%%%.###"}) {
			assertThat(firma.verificar(basura)).as(String.valueOf(basura)).isEmpty();
		}
	}

	/** R3: una clave corta impide arrancar. */
	@Test
	void claveCortaImpideArrancar() {
		assertThatThrownBy(() -> new FirmaRespuestaHmac("corta"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32");
	}

	/** R4: el token usa solo caracteres seguros para una dirección. */
	@Test
	void tokenSeguroParaUnaDireccion() {
		assertThat(firma.firmar(UUID.randomUUID())).matches("[A-Za-z0-9_.-]+");
	}

}
