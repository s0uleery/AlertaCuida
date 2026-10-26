package cl.ucn.alertacuida.adapters.seguridad.hash;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.application.contacto.puerto.HashMedioContacto;
import cl.ucn.alertacuida.domain.contacto.Correo;
import cl.ucn.alertacuida.domain.contacto.NumeroWhatsapp;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * whatsapp_hash y correo_hash = HMAC-SHA256 del valor normalizado, en hexadecimal. Usa la misma
 * clave que rut_hash ({@link HashRunHmac}), con un prefijo distinto para cada medio: así un mismo
 * texto no da el mismo hash como RUN, número o correo. Con clave, por la misma razón que el RUN:
 * los celulares posibles son pocos y sin clave se podrían probar todos.
 */
@Component
public class HashMedioContactoHmac implements HashMedioContacto {

	private static final String ALGORITMO = "HmacSHA256";

	private final SecretKeySpec clave;

	/** @param propiedades de ahí sale la clave del HMAC (alertacuida.seguridad.clave-run) */
	public HashMedioContactoHmac(PropiedadesSeguridad propiedades) {
		this.clave = new SecretKeySpec(propiedades.claveRun().getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	@Override
	public String de(NumeroWhatsapp numero) {
		return hmac("whatsapp:" + numero.valor());
	}

	@Override
	public String de(Correo correo) {
		return hmac("correo:" + correo.valor());
	}

	/** @return el HMAC del texto, en hexadecimal */
	private String hmac(String texto) {
		try {
			// Mac no es seguro entre hilos: uno por cálculo
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(clave);
			return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo calcular el hash del medio de contacto", e);
		}
	}

}
