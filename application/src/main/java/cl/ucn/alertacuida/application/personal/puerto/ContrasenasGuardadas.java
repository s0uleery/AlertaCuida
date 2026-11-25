package cl.ucn.alertacuida.application.personal.puerto;

import java.util.Optional;
import java.util.UUID;

/** El hash de la contraseña de una cuenta, para comprobar la actual antes de cambiarla. */
public interface ContrasenasGuardadas {

	/**
	 * @param idUsuario la cuenta
	 * @return el hash de su contraseña; vacío si no tiene (pendiente de activar)
	 */
	Optional<String> hashDe(UUID idUsuario);

}
