package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/reloj/v1/cancelaciones} (esquema Cancelacion): una alerta que el
 * paciente canceló en los 30 segundos. Solo para métricas.
 *
 * @param evidencia opcional
 */
public record SolicitudCancelacion(
		@NotNull UUID eventId,
		@NotNull TipoEvento tipoEvento,
		@NotNull Severidad severidad,
		@NotBlank @Size(max = 128) String versionConfiguracion,
		@NotNull Instant fechaHoraDeteccion,
		@NotNull Instant fechaHoraCancelacion,
		@Valid EvidenciaReloj evidencia) {
}
