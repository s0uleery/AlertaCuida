package cl.ucn.alertacuida.adapters.web.personal;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta del alta y de un enlace nuevo. El enlace se envía por correo a la persona; solo si el correo no
 * salió (falló, o está en modo simulado en desarrollo) viene aquí, para que quien administra se lo pase.
 *
 * @param idUsuario        cuenta creada o a la que pertenece el enlace
 * @param enlaceActivacion dirección completa, con el token después de "#"; null si salió por correo
 * @param expiraEn         hasta cuándo sirve el enlace
 * @param correoEnviado    si el enlace salió por correo a la persona
 */
public record RespuestaEnlace(UUID idUsuario, String enlaceActivacion, Instant expiraEn, boolean correoEnviado) {

	/** Sin el enlace: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "RespuestaEnlace[idUsuario=" + idUsuario + ", expiraEn=" + expiraEn + ", correoEnviado="
				+ correoEnviado + "]";
	}

}
