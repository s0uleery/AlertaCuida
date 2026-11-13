package cl.ucn.alertacuida.adapters.seguridad.firma;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Token de los enlaces de respuesta: {@code <aviso>.<firma>}, ambos en base64 para direcciones y sin
 * relleno. El aviso son los 16 bytes del id de registro_intento; la firma, el HMAC-SHA256 de esos bytes con
 * alertacuida.seguridad.clave-respuesta. Así no se guarda nada en la base y nadie puede crear un token sin
 * la clave. La clave es propia: filtrar la del RUN o la de los tokens del dashboard no permite responder.
 */
@Component
public class FirmaRespuestaHmac implements FirmaRespuesta {

	private static final String ALGORITMO = "HmacSHA256";
	private static final int LARGO_MINIMO = 32;
	private static final Base64.Encoder CODIFICADOR = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODIFICADOR = Base64.getUrlDecoder();

	private final SecretKeySpec clave;

	/** @param propiedades de ahí sale la clave del HMAC (alertacuida.seguridad.clave-respuesta) */
	@Autowired
	public FirmaRespuestaHmac(PropiedadesSeguridad propiedades) {
		this(propiedades.claveRespuesta());
	}

	/**
	 * @param clave la clave del HMAC
	 * @throws IllegalStateException si tiene menos de 32 caracteres: el backend no arranca
	 */
	public FirmaRespuestaHmac(String clave) {
		if (clave == null || clave.length() < LARGO_MINIMO) {
			throw new IllegalStateException("La clave de los enlaces de respuesta debe tener al menos "
					+ LARGO_MINIMO + " caracteres");
		}
		this.clave = new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	@Override
	public String firmar(UUID idIntento) {
		byte[] aviso = bytes(idIntento);
		return CODIFICADOR.encodeToString(aviso) + "." + CODIFICADOR.encodeToString(hmac(aviso));
	}

	/** Compara la firma en tiempo constante, para no dar pistas por cuánto tarda en rechazarla. */
	@Override
	public Optional<UUID> verificar(String token) {
		if (token == null) {
			return Optional.empty();
		}
		String[] partes = token.split("\\.", -1);
		if (partes.length != 2) {
			return Optional.empty();
		}
		try {
			byte[] aviso = DECODIFICADOR.decode(partes[0]);
			byte[] firma = DECODIFICADOR.decode(partes[1]);
			if (aviso.length != 16 || !MessageDigest.isEqual(hmac(aviso), firma)) {
				return Optional.empty();
			}
			ByteBuffer buffer = ByteBuffer.wrap(aviso);
			return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
		} catch (IllegalArgumentException malFormado) {
			return Optional.empty();
		}
	}

	/** @return el HMAC de esos bytes con la clave */
	private byte[] hmac(byte[] datos) {
		try {
			// Mac no es seguro entre hilos: uno por cálculo
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(clave);
			return mac.doFinal(datos);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo firmar el enlace de respuesta", e);
		}
	}

	/** @return los 16 bytes del id */
	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
				.array();
	}

}
