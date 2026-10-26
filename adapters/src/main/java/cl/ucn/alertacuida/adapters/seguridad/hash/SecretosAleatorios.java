package cl.ucn.alertacuida.adapters.seguridad.hash;

import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Tokens de los enlaces de un solo uso: 32 bytes aleatorios (256 bits) en Base64 apto para URL.
 *
 * <p>Se guardan con SHA-256 simple, sin sal ni bcrypt: a diferencia de una contraseña, el token es
 * aleatorio y largo, así que no se puede adivinar probando. Así se busca directo por el hash y,
 * si alguien lee la base, no obtiene enlaces que funcionen.
 */
@Component
public class SecretosAleatorios implements SecretosUnSoloUso {

	private static final int BYTES = 32;

	/** SecureRandom es seguro entre hilos: uno para toda la aplicación. */
	private final SecureRandom aleatorio = new SecureRandom();

	/** @return un token nuevo, sin relleno "=" para que no haya que codificarlo en la URL */
	@Override
	public String nuevo() {
		byte[] bytes = new byte[BYTES];
		aleatorio.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** @return SHA-256 del token, en hexadecimal (lo que va en token_accion.hash_token) */
	@Override
	public String hash(String secreto) {
		try {
			// MessageDigest no es seguro entre hilos: uno por cálculo
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha.digest(secreto.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no tiene SHA-256", e);
		}
	}

}
