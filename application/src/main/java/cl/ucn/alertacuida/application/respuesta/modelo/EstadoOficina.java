package cl.ucn.alertacuida.application.respuesta.modelo;

import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que registró la oficina PRLAC de una alerta (fila de atencion_alerta). Si la alerta no tiene fila, todo va
 * en null ({@link #VACIO}). Las fechas son null si ese paso no ocurrió.
 *
 * @param idResponsable     cuenta que la tiene a cargo
 * @param responsable       su nombre
 * @param resultadoLlamada  cómo resultó la llamada
 * @param personalAsignado  cuándo se asignó personal
 * @param sinDisponibilidad cuándo dijo que no había personal
 * @param llegada           cuándo llegó el personal
 * @param resultadoVisita   cómo lo encontró
 * @param cierre            cuándo se cerró
 * @param clasificacion     con qué se cerró
 */
public record EstadoOficina(UUID idResponsable, String responsable, ResultadoLlamadaOficina resultadoLlamada,
		Instant personalAsignado, Instant sinDisponibilidad, Instant llegada, ResultadoVisita resultadoVisita,
		Instant cierre, ClasificacionAlerta clasificacion) {

	/** Una alerta que la oficina no ha tocado. */
	public static final EstadoOficina VACIO = new EstadoOficina(null, null, null, null, null, null, null, null,
			null);

}
