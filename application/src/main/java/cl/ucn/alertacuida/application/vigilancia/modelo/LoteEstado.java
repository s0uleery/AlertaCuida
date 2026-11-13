package cl.ucn.alertacuida.application.vigilancia.modelo;

import java.time.Instant;

/**
 * Lo que informó el reloj en una sincronización, reducido a lo que miran las reglas de ausencia.
 *
 * @param recepcion    cuándo llegó al servidor
 * @param nivelBateria porcentaje de batería; null si no lo informó
 * @param relojPuesto  si lo tenía puesto; null si no lo informó
 */
public record LoteEstado(Instant recepcion, Integer nivelBateria, Boolean relojPuesto) {
}
