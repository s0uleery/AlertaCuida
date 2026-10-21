package cl.ucn.alertacuida.application.sesion.puerto;

import cl.ucn.alertacuida.application.sesion.CuentaUsuario;
import java.util.Optional;
import java.util.Set;

public interface CuentaUsuarioRepository {

	Optional<CuentaUsuario> buscarPorRunHash(String runHash);

	/** Permisos del rol como "MODULO:ACCION". */
	Set<String> permisosDelRol(String codigoRol);

}
