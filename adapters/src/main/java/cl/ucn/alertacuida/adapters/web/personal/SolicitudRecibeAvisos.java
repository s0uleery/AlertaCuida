package cl.ucn.alertacuida.adapters.web.personal;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PUT /api/personal/{id}/recibe-avisos}.
 *
 * @param recibeAvisos si recibe por correo los avisos de las alertas técnicas; obligatorio
 */
public record SolicitudRecibeAvisos(@NotNull Boolean recibeAvisos) {
}
