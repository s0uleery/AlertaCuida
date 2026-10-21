package cl.ucn.alertacuida.adapters.web.sesion;

import jakarta.validation.constraints.NotBlank;

/** El RUN puede venir con o sin puntos (11.111.111-1 o 11111111-1). */
public record SolicitudLogin(@NotBlank String run, @NotBlank String contrasena) {

	/** Sin la contraseña: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "SolicitudLogin[run=" + run + "]";
	}

}
