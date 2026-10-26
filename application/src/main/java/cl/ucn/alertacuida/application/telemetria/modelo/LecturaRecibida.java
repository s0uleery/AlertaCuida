package cl.ucn.alertacuida.application.telemetria.modelo;

import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Instant;

/**
 * Una medición de FC o SpO2 tal como la envió el reloj.
 *
 * @param medicion hora real de la medición en el reloj (no la de envío)
 */
public record LecturaRecibida(TipoLectura tipo, double valor, Instant medicion) {
}
