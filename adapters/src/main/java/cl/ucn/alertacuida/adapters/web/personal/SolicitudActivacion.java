package cl.ucn.alertacuida.adapters.web.personal;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code POST /api/auth/activar}.
 *
 * @param token      lo que viene después de "#" en el enlace
 * @param contrasena la que eligió la persona
 */
public record SolicitudActivacion(@NotBlank String token, @NotBlank String contrasena) {

	/** Sin el token ni la contraseña: que no aparezcan en los logs. */
	@Override
	public String toString() {
		return "SolicitudActivacion[]";
	}

}
