package cl.ucn.alertacuida.adapters.seguridad.firma;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Revisa la firma de los webhooks de Meta: la cabecera {@code X-Hub-Signature-256} trae {@code sha256=} y el
 * HMAC-SHA256 del cuerpo, en hexadecimal, calculado con el App Secret de la app de Meta. Se calcula sobre los
 * bytes tal como llegan, antes de leer el JSON, y se compara en tiempo constante.
 */
public class FirmaWebhookMeta {

	private static final String PREFIJO = "sha256=";
	private static final String ALGORITMO = "HmacSHA256";

	private final SecretKeySpec llave;

	/** @param appSecret el App Secret de la app de Meta; vacío si WhatsApp no está configurado (nada es válido) */
	public FirmaWebhookMeta(String appSecret) {
		this.llave = appSecret == null || appSecret.isBlank() ? null
				: new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	/**
	 * @param cuerpo   el cuerpo del POST, en bytes, sin tocar
	 * @param cabecera el valor de X-Hub-Signature-256; puede faltar
	 * @return si la firma corresponde a ese cuerpo
	 */
	public boolean esValida(byte[] cuerpo, String cabecera) {
		if (llave == null || cabecera == null || !cabecera.startsWith(PREFIJO)) {
			return false;
		}
		byte[] recibida;
		try {
			recibida = HexFormat.of().parseHex(cabecera.substring(PREFIJO.length()).strip());
		} catch (IllegalArgumentException noEsHex) {
			return false;
		}
		return MessageDigest.isEqual(calcular(cuerpo), recibida);
	}

	/** @return el HMAC-SHA256 del cuerpo */
	private byte[] calcular(byte[] cuerpo) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(llave);
			return mac.doFinal(cuerpo);
		} catch (GeneralSecurityException imposible) {
			throw new IllegalStateException("HmacSHA256 no está disponible", imposible);
		}
	}

}
