package cl.ucn.alertacuida.application.alerta.modelo;

import java.time.Instant;

/**
 * Los pasos que la oficina dio desde el enlace del correo (columnas de V19 en atencion_alerta), para la línea de
 * tiempo. Cada fecha es null si ese paso no ocurrió.
 *
 * @param resultadoLlamada  BIEN, NECESITA_ATENCION o NO_CONTESTA; null sin llamada
 * @param llamada           cuándo registró la llamada
 * @param personalAsignado  cuándo asignó personal
 * @param sinDisponibilidad cuándo dijo que no había personal
 * @param llegada           cuándo llegó el personal
 * @param resultadoVisita   BIEN, APOYO_NO_URGENTE o EMERGENCIA; null sin resultado
 * @param visita            cuándo registró el resultado
 */
public record PasosOficinaRegistrados(String resultadoLlamada, Instant llamada, Instant personalAsignado,
		Instant sinDisponibilidad, Instant llegada, String resultadoVisita, Instant visita) {
}
