package cl.ucn.alertacuida.adapters.seguridad;

import cl.ucn.alertacuida.application.sesion.puerto.VerificadorContrasena;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class VerificadorBcrypt implements VerificadorContrasena {

	private final PasswordEncoder encoder;
	/** Hash de relleno: sin él, un RUN inexistente respondería más rápido y se notaría. */
	private final String hashFicticio;

	public VerificadorBcrypt(PasswordEncoder encoder) {
		this.encoder = encoder;
		this.hashFicticio = encoder.encode(UUID.randomUUID().toString());
	}

	@Override
	public boolean coincide(String contrasena, String hash) {
		if (hash == null) {
			encoder.matches(contrasena == null ? "" : contrasena, hashFicticio);
			return false;
		}
		return encoder.matches(contrasena, hash);
	}

}
