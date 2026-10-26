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
 * Cuerpo de {@code POST /api/reloj/v1/alertas} (esquema Alerta). Aquí solo se revisa la forma:
 * el contenido nunca hace rechazar una alerta.
 *
 * @param esperaDiaHabil true si es leve fuera del horario hábil y el reloj no avisará a nadie
 * @param evidencia      opcional
 */
public record SolicitudAlerta(
		@NotNull UUID eventId,
		@NotNull TipoEvento tipoEvento,
		@NotNull Severidad severidad,
		@NotBlank @Size(max = 128) String versionConfiguracion,
		@NotNull Instant fechaHoraDeteccion,
		@NotNull Instant fechaHoraEnvio,
		@NotNull Boolean esperaDiaHabil,
		@Valid EvidenciaReloj evidencia) {
}
