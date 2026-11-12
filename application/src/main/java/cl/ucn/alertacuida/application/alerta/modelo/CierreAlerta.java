package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import java.time.Instant;

/**
 * Cómo se cerró una alerta.
 *
 * @param resolutor     nombre de quien la resolvió; null si la cerró el sistema
 * @param clasificacion null en las alertas técnicas
 * @param observaciones null si la cerró el sistema
 */
public record CierreAlerta(Instant fechaHora, String resolutor, ClasificacionAlerta clasificacion,
		String observaciones) {
}
