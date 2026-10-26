package cl.ucn.alertacuida.adapters.seguridad.hash;

import cl.ucn.alertacuida.adapters.seguridad.ConfiguracionSeguridad;
import cl.ucn.alertacuida.application.personal.puerto.HashContrasena;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hash de las contraseñas nuevas con bcrypt, el mismo {@link PasswordEncoder} con que
 * {@link VerificadorBcrypt} las compara al iniciar sesión.
 */
@Component
public class HashContrasenaBcrypt implements HashContrasena {

	private final PasswordEncoder encoder;

	/** @param encoder bcrypt, definido en {@link ConfiguracionSeguridad} */
	public HashContrasenaBcrypt(PasswordEncoder encoder) {
		this.encoder = encoder;
	}

	/** @return el hash bcrypt, con su sal incluida (lo que va en usuario.hash_contrasena) */
	@Override
	public String de(String contrasena) {
		return encoder.encode(contrasena);
	}

}
