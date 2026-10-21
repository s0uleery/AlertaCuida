package cl.ucn.alertacuida.application.sesion.puerto;

import java.time.Instant;
import java.util.UUID;

/** LOGIN y LOGIN_FALLIDO en la auditoría: de ahí salen el último acceso y el bloqueo. */
public interface RegistroAccesos {

	void registrarLogin(UUID idUsuario, String ip, Instant fechaHora);

	/** @param idUsuario vacío si el RUN no existe */
	void registrarLoginFallido(UUID idUsuario, String ip, Instant fechaHora);

	/** LOGIN_FALLIDO desde {@code desde} y posteriores a su último LOGIN. */
	long contarFallidosRecientes(UUID idUsuario, Instant desde);

}
