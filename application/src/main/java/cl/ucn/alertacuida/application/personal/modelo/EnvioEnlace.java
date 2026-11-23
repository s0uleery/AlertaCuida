package cl.ucn.alertacuida.application.personal.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de enviar el enlace de activación por correo.
 *
 * @param idUsuario     cuenta a la que pertenece el enlace
 * @param enlace        la página de activación con el token después de "#"
 * @param expiraEn      hasta cuándo sirve
 * @param correoEnviado si salió por correo a la persona. Si es false (el envío falló o el correo está en modo
 *                      simulado), quien administra debe pasarle el enlace por otra vía
 */
public record EnvioEnlace(UUID idUsuario, String enlace, Instant expiraEn, boolean correoEnviado) {

	/** Sin el enlace: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "EnvioEnlace[idUsuario=" + idUsuario + ", expiraEn=" + expiraEn + ", correoEnviado=" + correoEnviado
				+ "]";
	}

}
