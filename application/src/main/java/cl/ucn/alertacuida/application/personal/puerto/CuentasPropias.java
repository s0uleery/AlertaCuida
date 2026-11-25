package cl.ucn.alertacuida.application.personal.puerto;

import cl.ucn.alertacuida.application.personal.modelo.MiCuenta;
import java.util.Optional;
import java.util.UUID;

/** Lectura de la cuenta propia de quien tiene la sesión, para "Mi cuenta". */
public interface CuentasPropias {

	/**
	 * @param id la cuenta de la sesión
	 * @return sus datos, con el teléfono descifrado; vacío si la cuenta ya no existe
	 */
	Optional<MiCuenta> buscar(UUID id);

}
