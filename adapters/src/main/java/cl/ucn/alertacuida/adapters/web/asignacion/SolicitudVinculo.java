package cl.ucn.alertacuida.adapters.web.asignacion;

import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/pacientes/{id}/reloj} (vincular) y {@code .../reloj/reemplazo}.
 *
 * @param idDispositivo       reloj que recibe el paciente
 * @param estadoRelojAnterior solo al reemplazar: en qué estado queda el reloj anterior
 *                            (ej. EN_REPARACION); vacío = sin cambio
 */
public record SolicitudVinculo(@NotNull UUID idDispositivo, EstadoReloj estadoRelojAnterior) {
}
