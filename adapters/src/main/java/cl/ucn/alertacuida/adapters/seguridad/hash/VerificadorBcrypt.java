package cl.ucn.alertacuida.adapters.seguridad.hash;

import cl.ucn.alertacuida.adapters.seguridad.ConfiguracionSeguridad;
import cl.ucn.alertacuida.application.sesion.puerto.VerificadorContrasena;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Compara la contraseña escrita con el hash bcrypt guardado. bcrypt es lento a propósito
 * (unos 100 ms por intento): así, probar millones de contraseñas tomaría años.
 */
@Component
public class VerificadorBcrypt implements VerificadorContrasena {

	private final PasswordEncoder encoder;
	/** Hash de relleno: sin él, un RUN inexistente respondería más rápido y se notaría. */
	private final String hashFicticio;

	/** @param encoder bcrypt, definido en {@link ConfiguracionSeguridad} */
	public VerificadorBcrypt(PasswordEncoder encoder) {
		this.encoder = encoder;
		this.hashFicticio = encoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * @param contrasena la escrita al iniciar sesión
	 * @param hash       el guardado, o vacío si no hay cuenta o no está activada
	 * @return si coinciden; siempre false con hash vacío, pero tardando lo mismo
	 */
	@Override
	public boolean coincide(String contrasena, String hash) {
		if (hash == null) {
			encoder.matches(contrasena == null ? "" : contrasena, hashFicticio);
			return false;
		}
		return encoder.matches(contrasena, hash);
	}

}
