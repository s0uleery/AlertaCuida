package cl.ucn.alertacuida.application.personal.puerto;

import cl.ucn.alertacuida.application.personal.error.CuentaDuplicada;
import cl.ucn.alertacuida.application.personal.modelo.CuentaPendiente;
import cl.ucn.alertacuida.application.personal.modelo.NuevaCuenta;
import java.util.Optional;
import java.util.UUID;

/** Cuentas del personal del PRLAC (tabla usuario), tal como las necesitan el alta y la activación. */
public interface CuentasPersonal {

	/**
	 * @param runHash HMAC del RUN normalizado
	 * @return si ya hay una cuenta con ese RUN
	 */
	boolean existeRunHash(String runHash);

	/**
	 * @param correo en minúsculas
	 * @return si ya hay una cuenta con ese correo
	 */
	boolean existeCorreo(String correo);

	/**
	 * @param codigoRol por ejemplo, PERSONAL
	 * @return si el rol existe y no está desactivado
	 */
	boolean rolActivo(String codigoRol);

	/**
	 * Guarda una cuenta inactiva y sin contraseña.
	 *
	 * @return el identificador de la cuenta creada
	 * @throws CuentaDuplicada si otra petición creó el mismo RUN o correo antes
	 */
	UUID crear(NuevaCuenta cuenta);

	/**
	 * @param id cuenta buscada
	 * @return la cuenta, solo si todavía no tiene contraseña
	 */
	Optional<CuentaPendiente> buscarPendiente(UUID id);

	/**
	 * Guarda la contraseña y activa la cuenta, solo si seguía pendiente.
	 *
	 * @param hashContrasena hash de la contraseña elegida, nunca la contraseña
	 * @return si la activó; false si otra petición la activó antes
	 */
	boolean activar(UUID id, String hashContrasena);

}
