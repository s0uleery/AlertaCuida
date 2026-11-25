package cl.ucn.alertacuida.adapters.seguridad.firma;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Firma de los webhooks de Meta, según su guía: {@code X-Hub-Signature-256: sha256=} + HMAC-SHA256 del cuerpo
 * con el App Secret. El valor esperado se calcula aparte con la API estándar, no con la clase probada.
 */
class FirmaWebhookMetaTest {

	private static final String SECRETO = "app-secret-de-prueba";
	private static final byte[] CUERPO = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

	private final FirmaWebhookMeta firma = new FirmaWebhookMeta(SECRETO);

	/** La firma de Meta sobre ese cuerpo es válida. */
	@Test
	void laFirmaCorrectaEsValida() throws Exception {
		assertThat(firma.esValida(CUERPO, "sha256=" + hmac(SECRETO, CUERPO))).isTrue();
	}

	/** Basta un byte distinto en el cuerpo para que no sirva. */
	@Test
	void otroCuerpoNoEsValido() throws Exception {
		byte[] otro = "{\"object\":\"whatsapp_business_account\" }".getBytes(StandardCharsets.UTF_8);

		assertThat(firma.esValida(otro, "sha256=" + hmac(SECRETO, CUERPO))).isFalse();
	}

	/** Firmada con otro secreto, sin prefijo, sin cabecera o con basura, no es válida. */
	@Test
	void firmasMalasNoSonValidas() throws Exception {
		assertThat(firma.esValida(CUERPO, "sha256=" + hmac("otro-secreto", CUERPO))).isFalse();
		assertThat(firma.esValida(CUERPO, hmac(SECRETO, CUERPO))).isFalse();
		assertThat(firma.esValida(CUERPO, null)).isFalse();
		assertThat(firma.esValida(CUERPO, "sha256=no-es-hex")).isFalse();
	}

	/** Sin App Secret configurado, nada es válido. */
	@Test
	void sinSecretoNadaEsValido() throws Exception {
		String cualquiera = "sha256=" + hmac("x", CUERPO);

		assertThat(new FirmaWebhookMeta("").esValida(CUERPO, cualquiera)).isFalse();
		assertThat(new FirmaWebhookMeta(null).esValida(CUERPO, cualquiera)).isFalse();
	}

	/** @return el HMAC-SHA256 en hexadecimal, calculado con la API estándar */
	private static String hmac(String secreto, byte[] cuerpo) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(cuerpo));
	}

}
