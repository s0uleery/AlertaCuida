package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.application.personal.modelo.CambioRol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/personal/{id}/rol}.
 *
 * @param rol            código del rol nuevo
 * @param telefono       celular con WhatsApp; el dashboard lo pide solo al pasar a COORDINADOR a una
 *                       cuenta que no lo tiene. Vacío deja el teléfono como está
 * @param aceptaWhatsapp si la persona aceptó recibir los avisos por WhatsApp; vacío = no
 */
public record SolicitudCambioRol(
		@NotBlank String rol,
		@Size(max = 30) String telefono,
		Boolean aceptaWhatsapp) {

	/** @return los mismos datos en el formato del caso de uso */
	CambioRol cambio() {
		return new CambioRol(rol, telefono, Boolean.TRUE.equals(aceptaWhatsapp));
	}

}
