package cl.ucn.alertacuida.adapters.web.asignacion;

import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/pacientes/{id}/reloj} (vincular) y {@code .../reloj/reemplazo}.
 *
 * @param idDispositivo        reloj que recibe el paciente
 * @param estadoRelojAnterior  solo al reemplazar: en qué estado queda el reloj anterior
 *                             (ej. EN_REPARACION); vacío = sin cambio
 * @param codigoEmparejamiento el que muestra el reloj (ej. "K7PM-42QX"); obligatorio
 */
public record SolicitudVinculo(@NotNull UUID idDispositivo, EstadoReloj estadoRelojAnterior,
		@NotBlank @Size(max = 20) String codigoEmparejamiento) {
}
