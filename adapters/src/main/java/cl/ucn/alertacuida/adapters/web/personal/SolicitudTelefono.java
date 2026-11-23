package cl.ucn.alertacuida.adapters.web.personal;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PUT /api/personal/{id}/telefono}.
 *
 * @param telefono       celular con WhatsApp; vacío o ausente para quitarlo (no en un coordinador)
 * @param aceptaWhatsapp si la persona aceptó recibir WhatsApp; obligatorio
 */
public record SolicitudTelefono(String telefono, @NotNull Boolean aceptaWhatsapp) {
}
