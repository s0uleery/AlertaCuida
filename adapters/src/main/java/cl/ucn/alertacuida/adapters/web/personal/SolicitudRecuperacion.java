package cl.ucn.alertacuida.adapters.web.personal;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code POST /api/auth/recuperar}.
 *
 * @param run el RUN de la persona que olvidó su contraseña, con o sin puntos
 */
public record SolicitudRecuperacion(@NotBlank String run) {

	/** Sin el RUN: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "SolicitudRecuperacion[]";
	}

}
