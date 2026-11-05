package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.application.personal.modelo.DatosCuenta;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/personal}. Aquí solo se revisa la forma (campos presentes, correo
 * bien escrito); las reglas del negocio (RUN válido, rol activo, duplicados) están en el caso de uso.
 *
 * @param run   con o sin puntos
 * @param cargo opcional
 * @param rol            código del rol: ADMINISTRADOR, COORDINADOR o PERSONAL
 * @param telefono       celular con WhatsApp; obligatorio para COORDINADOR (lo revisa el caso de uso)
 * @param aceptaWhatsapp si la persona aceptó recibir los avisos por WhatsApp; vacío = no
 */
public record SolicitudCuenta(
		@NotBlank String run,
		@NotBlank @Size(max = 200) String nombre,
		@Size(max = 200) String cargo,
		@NotBlank @Email String correo,
		@NotBlank String rol,
		@Size(max = 30) String telefono,
		Boolean aceptaWhatsapp) {

	/** @return los mismos datos en el formato del caso de uso */
	DatosCuenta datos() {
		return new DatosCuenta(run, nombre, cargo, correo, rol, telefono, Boolean.TRUE.equals(aceptaWhatsapp));
	}

}
