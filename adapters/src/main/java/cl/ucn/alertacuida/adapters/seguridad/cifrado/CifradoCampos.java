package cl.ucn.alertacuida.adapters.seguridad.cifrado;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Cifra los datos personales antes de guardarlos y los descifra al leerlos. Lo usan los
 * adaptadores JDBC; los casos de uso no se enteran.
 *
 * <p>AES-256-GCM: además de ocultar el dato, detecta si fue alterado. Cada valor lleva su propio
 * vector inicial aleatorio, así que dos apellidos iguales quedan distintos en la base. Se guarda
 * como texto: {@code "v1:" + base64(vector inicial + dato cifrado + etiqueta)}. El prefijo indica
 * la llave usada, para poder cambiarla más adelante sin perder los datos antiguos.
 */
@Component
public class CifradoCampos {

	/** Prefijo de los valores cifrados con la llave actual. */
	static final String VERSION = "v1:";

	private static final String ALGORITMO = "AES/GCM/NoPadding";
	private static final int BYTES_LLAVE = 32;
	private static final int BYTES_VECTOR = 12;
	private static final int BITS_ETIQUETA = 128;

	private final SecretKey llave;
	private final SecureRandom azar = new SecureRandom();

	/**
	 * @param propiedades de ahí sale la llave (alertacuida.seguridad.clave-cifrado): 32 bytes en base64
	 * @throws IllegalStateException si la llave no es base64 o no tiene 32 bytes: así el backend no arranca
	 */
	public CifradoCampos(PropiedadesSeguridad propiedades) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(propiedades.claveCifrado());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("La llave de cifrado (CIFRADO_CLAVE) no está en base64", e);
		}
		if (bytes.length != BYTES_LLAVE) {
			throw new IllegalStateException("La llave de cifrado (CIFRADO_CLAVE) debe tener 32 bytes; tiene "
					+ bytes.length);
		}
		this.llave = new SecretKeySpec(bytes, "AES");
	}

	/**
	 * @param texto   dato en claro, o null
	 * @param columna dónde se guardará; un valor cifrado para otra columna no se descifra
	 * @return el valor para guardar, o null si el texto era null
	 */
	public String cifrar(String texto, ColumnaCifrada columna) {
		if (texto == null) {
			return null;
		}
		byte[] vector = new byte[BYTES_VECTOR];
		azar.nextBytes(vector);
		byte[] cifrado = ejecutar(Cipher.ENCRYPT_MODE, vector, columna, texto.getBytes(StandardCharsets.UTF_8));
		byte[] junto = ByteBuffer.allocate(vector.length + cifrado.length).put(vector).put(cifrado).array();
		return VERSION + Base64.getEncoder().encodeToString(junto);
	}

	/**
	 * @param guardado valor leído de la base, o null
	 * @param columna  de dónde se leyó
	 * @return el dato en claro, o null si no había valor
	 * @throws IllegalStateException si el valor no está cifrado, fue alterado, es de otra columna o
	 *                               se cifró con otra llave
	 */
	public String descifrar(String guardado, ColumnaCifrada columna) {
		if (guardado == null) {
			return null;
		}
		if (!guardado.startsWith(VERSION)) {
			throw new IllegalStateException("Valor sin cifrar o de una versión desconocida en " + columna.nombre());
		}
		byte[] junto;
		try {
			junto = Base64.getDecoder().decode(guardado.substring(VERSION.length()));
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("Valor cifrado dañado en " + columna.nombre(), e);
		}
		if (junto.length <= BYTES_VECTOR) {
			throw new IllegalStateException("Valor cifrado dañado en " + columna.nombre());
		}
		ByteBuffer lector = ByteBuffer.wrap(junto);
		byte[] vector = new byte[BYTES_VECTOR];
		lector.get(vector);
		byte[] cifrado = new byte[lector.remaining()];
		lector.get(cifrado);
		return new String(ejecutar(Cipher.DECRYPT_MODE, vector, columna, cifrado), StandardCharsets.UTF_8);
	}

	/** Cifra o descifra con la columna como dato asociado. Cipher no es seguro entre hilos: uno por llamada. */
	private byte[] ejecutar(int modo, byte[] vector, ColumnaCifrada columna, byte[] datos) {
		try {
			Cipher cifrador = Cipher.getInstance(ALGORITMO);
			cifrador.init(modo, llave, new GCMParameterSpec(BITS_ETIQUETA, vector));
			cifrador.updateAAD(columna.nombre().getBytes(StandardCharsets.UTF_8));
			return cifrador.doFinal(datos);
		} catch (GeneralSecurityException e) {
			// Sin detalles del dato: el mensaje puede terminar en un registro
			throw new IllegalStateException("No se pudo " + (modo == Cipher.ENCRYPT_MODE ? "cifrar" : "descifrar")
					+ " un valor de " + columna.nombre(), e);
		}
	}

}
