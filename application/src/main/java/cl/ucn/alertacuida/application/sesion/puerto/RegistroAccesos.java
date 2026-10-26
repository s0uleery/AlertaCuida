package cl.ucn.alertacuida.application.sesion.puerto;

import java.time.Instant;
import java.util.UUID;

/** LOGIN y LOGIN_FALLIDO en la auditoría: de ahí salen el último acceso y el bloqueo. */
public interface RegistroAccesos {

	/**
	 * Registra un inicio de sesión correcto.
	 *
	 * @param idUsuario quien entró
	 * @param ip        desde dónde
	 * @param fechaHora cuándo
	 */
	void registrarLogin(UUID idUsuario, String ip, Instant fechaHora);

	/**
	 * Registra un intento fallido.
	 *
	 * @param idUsuario vacío si el RUN no existe
	 * @param ip        desde dónde
	 * @param fechaHora cuándo
	 */
	void registrarLoginFallido(UUID idUsuario, String ip, Instant fechaHora);

	/**
	 * @param idUsuario cuenta revisada
	 * @param desde     inicio de la ventana del bloqueo
	 * @return cuántos LOGIN_FALLIDO hay desde {@code desde} y posteriores a su último LOGIN
	 */
	long contarFallidosRecientes(UUID idUsuario, Instant desde);

}
