package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import java.time.Instant;
import java.util.List;

/**
 * Todo lo que quedó registrado de una alerta. El modelo de datos no guarda la línea de tiempo: se arma
 * con estas fechas. Cada fecha es null si ese paso no ocurrió.
 *
 * @param resolutor     nombre de quien la resolvió; null si la cerró el sistema
 * @param observaciones ya descifradas
 * @param intentos      en el orden en que se hicieron
 * @param seguimientos  en orden de fecha
 */
public record HistorialAlerta(FilaAlerta alerta, Instant agravamiento, Instant cancelacion, Instant sinRespuesta,
		Instant toma, String responsable, Instant cierre, String resolutor, ClasificacionAlerta clasificacion,
		String observaciones, Instant cierreSeguimiento, List<IntentoRegistrado> intentos,
		List<AvisoRelojRegistrado> avisosReloj, List<AccionSeguimiento> seguimientos) {
}
