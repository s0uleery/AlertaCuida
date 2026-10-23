package cl.ucn.alertacuida.adapters.web.personal;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta del alta y de un enlace nuevo. Mientras no exista el correo, el administrador
 * copia el enlace y se lo pasa a la persona; después, el enlace se enviará por correo y no vendrá aquí.
 *
 * @param idUsuario        cuenta creada o a la que pertenece el enlace
 * @param enlaceActivacion dirección completa, con el token después de "#"
 * @param expiraEn         hasta cuándo sirve el enlace
 */
public record RespuestaEnlace(UUID idUsuario, String enlaceActivacion, Instant expiraEn) {

	/** Sin el enlace: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "RespuestaEnlace[idUsuario=" + idUsuario + ", expiraEn=" + expiraEn + "]";
	}

}
