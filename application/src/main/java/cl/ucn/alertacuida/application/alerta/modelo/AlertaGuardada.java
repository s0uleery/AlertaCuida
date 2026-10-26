package cl.ucn.alertacuida.application.alerta.modelo;

import java.util.UUID;

/**
 * Una alerta ya guardada, buscada por su eventId.
 *
 * @param numero    folio legible para el equipo (#142)
 * @param cancelada si llegó como cancelación del paciente
 */
public record AlertaGuardada(UUID idAlerta, UUID idAsignacion, long numero, boolean cancelada) {
}
