package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.application.escalamiento.modelo.SolicitudAviso;
import cl.ucn.alertacuida.domain.contacto.Canal;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /alertas/{eventId}/avisos} (esquema SolicitudAviso de docs/api-reloj.yaml).
 *
 * @param eventId    de esta solicitud; repetirlo no envía dos veces
 * @param idContacto el de la configuración (vínculo con el paciente)
 * @param canal      WHATSAPP o CORREO
 * @param ronda      1 o 2; otro valor lo rechaza el caso de uso
 */
public record SolicitudAvisoReloj(@NotNull UUID eventId, @NotNull UUID idContacto, @NotNull Canal canal,
		@NotNull Integer ronda) {

	/** @return los mismos datos en el formato del caso de uso */
	SolicitudAviso solicitud() {
		return new SolicitudAviso(eventId, idContacto, canal, ronda);
	}

}
