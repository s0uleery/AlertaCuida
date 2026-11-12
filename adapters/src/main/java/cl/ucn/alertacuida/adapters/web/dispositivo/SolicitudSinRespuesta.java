package cl.ucn.alertacuida.adapters.web.dispositivo;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Cuerpo de {@code POST /alertas/{eventId}/sin-respuesta}.
 *
 * @param fechaHora cuándo terminó el reloj de recorrer la lista
 */
public record SolicitudSinRespuesta(@NotNull Instant fechaHora) {
}
