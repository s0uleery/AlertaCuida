package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.UUID;

/**
 * Una alerta ya guardada, buscada por su eventId.
 *
 * @param numero    folio legible para el equipo (#142)
 * @param tipo      qué se detectó
 * @param severidad gravedad con que llegó
 * @param cancelada si llegó como cancelación del paciente
 * @param agravada  si una caída ya pasó a grave por inmovilidad
 */
public record AlertaGuardada(UUID idAlerta, UUID idAsignacion, long numero, TipoEvento tipo, Severidad severidad,
		boolean cancelada, boolean agravada) {
}
