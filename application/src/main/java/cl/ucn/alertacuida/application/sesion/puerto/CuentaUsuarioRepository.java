package cl.ucn.alertacuida.application.sesion.puerto;

import cl.ucn.alertacuida.application.sesion.modelo.CuentaUsuario;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Cuentas del personal tal como las necesita el inicio de sesión. */
public interface CuentaUsuarioRepository {

	/**
	 * @param runHash HMAC del RUN normalizado
	 * @return la cuenta con ese RUN, si existe
	 */
	Optional<CuentaUsuario> buscarPorRunHash(String runHash);

	/**
	 * @param id identificador de la cuenta
	 * @return la cuenta, si existe; la renovación la usa para ver si sigue activa
	 */
	Optional<CuentaUsuario> buscarPorId(UUID id);

	/**
	 * @param codigoRol por ejemplo, PERSONAL
	 * @return permisos del rol como "MODULO:ACCION" (ej. "ALERTAS:VER")
	 */
	Set<String> permisosDelRol(String codigoRol);

}
