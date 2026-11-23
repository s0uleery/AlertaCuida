package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Instant;

/**
 * Una lectura de la evidencia de una alerta. Al recibirla se compara con los umbrales vigentes; en la
 * ficha de la alerta se muestra con su hora.
 *
 * @param tipo              FC o SPO2
 * @param valor             en latidos por minuto o en %
 * @param fechaHoraMedicion cuándo se midió, en la hora del reloj
 */
public record LecturaEvidencia(TipoLectura tipo, double valor, Instant fechaHoraMedicion) {
}
