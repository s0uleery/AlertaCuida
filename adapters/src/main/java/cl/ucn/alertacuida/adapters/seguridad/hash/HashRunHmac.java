package cl.ucn.alertacuida.adapters.seguridad.hash;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * rut_hash = HMAC-SHA256 del RUN normalizado, en hexadecimal. Con clave y no un SHA-256 simple:
 * los RUN posibles son pocos y sin clave se podrían probar todos para revertir el hash.
 */
@Component
public class HashRunHmac implements HashRun {

	private static final String ALGORITMO = "HmacSHA256";

	private final SecretKeySpec clave;

	public HashRunHmac(PropiedadesSeguridad propiedades) {
		this.clave = new SecretKeySpec(propiedades.claveRun().getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	@Override
	public String de(Run run) {
		try {
			// Mac no es seguro entre hilos: uno por cálculo
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(clave);
			return HexFormat.of().formatHex(mac.doFinal(run.valor().getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo calcular el hash del RUN", e);
		}
	}

}
