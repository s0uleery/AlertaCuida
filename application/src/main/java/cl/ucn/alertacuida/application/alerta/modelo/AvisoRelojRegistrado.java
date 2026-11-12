package cl.ucn.alertacuida.application.alerta.modelo;

import java.time.Instant;

/**
 * Un aviso al reloj sobre una alerta.
 *
 * @param envio     último envío; null mientras esté pendiente
 * @param recepcion cuándo informó el reloj que le llegó; null si aún no
 * @param intentos  cuántas veces se envió
 */
public record AvisoRelojRegistrado(String tipo, Instant envio, Instant recepcion, int intentos) {
}
