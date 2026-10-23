package cl.ucn.alertacuida.application.personal.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Enlace de un solo uso para que la persona elija su contraseña. El token solo existe aquí:
 * la base guarda su hash, así que si se pierde hay que emitir otro.
 */
public record EnlaceActivacion(UUID idUsuario, String token, Instant expiraEn) {

	/** Sin el token: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "EnlaceActivacion[idUsuario=" + idUsuario + ", expiraEn=" + expiraEn + "]";
	}

}
