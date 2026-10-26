package cl.ucn.alertacuida.application.asignacion.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Un período en que el paciente usó un reloj (asignacion_reloj). Nunca se borra: así se sabe
 * qué reloj envió cada dato aunque después se haya reemplazado.
 *
 * @param identificadorReloj el ID que informa el reloj, para mostrar
 * @param fin                vacío = vigente
 */
public record AsignacionHistorial(UUID idAsignacion, UUID idDispositivo, String identificadorReloj, Instant inicio,
		Instant fin) {
}
