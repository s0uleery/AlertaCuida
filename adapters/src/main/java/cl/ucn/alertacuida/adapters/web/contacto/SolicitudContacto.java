package cl.ucn.alertacuida.adapters.web.contacto;

import cl.ucn.alertacuida.application.contacto.modelo.DatosContacto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * Cuerpo para agregar o editar un contacto. Aquí solo se revisa la forma; formato del número,
 * del correo, horario y demás reglas están en el caso de uso.
 *
 * @param nombre            nombre de la persona o institución
 * @param whatsapp          número de WhatsApp (opcional si hay correo)
 * @param correo            correo (opcional si hay WhatsApp)
 * @param tipo              código del tipo de contacto (FAMILIA, VECINO, …)
 * @param parentesco        solo si el tipo es FAMILIA
 * @param cuidadorPrincipal si es el cuidador principal
 * @param horaInicio        "HH:mm"; vacía junto con horaFin = todo el día
 * @param horaFin           "HH:mm"
 */
public record SolicitudContacto(@NotBlank @Size(max = 120) String nombre, @Size(max = 30) String whatsapp,
		@Size(max = 254) String correo, @NotBlank String tipo, @Size(max = 60) String parentesco,
		boolean cuidadorPrincipal, LocalTime horaInicio, LocalTime horaFin) {

	/** @return los datos para el caso de uso */
	public DatosContacto datos() {
		return new DatosContacto(nombre, whatsapp, correo, tipo, parentesco, cuidadorPrincipal, horaInicio, horaFin);
	}

}
