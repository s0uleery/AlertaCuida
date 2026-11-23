package cl.ucn.alertacuida.adapters.web.dispositivo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Cuerpo de {@code POST /api/reloj/v1/alertas/{eventId}/agravamiento} (esquema Agravamiento de
 * docs/api-reloj.yaml): una caída leve o media que pasa a grave por inmovilidad.
 *
 * @param fechaHoraAgravamiento cuándo pasó a grave, en UTC
 * @param segundosInmovilidad   cuánto tiempo quedó sin moverse (opcional)
 */
public record SolicitudAgravamiento(@NotNull Instant fechaHoraAgravamiento, @Min(0) Integer segundosInmovilidad) {
}
