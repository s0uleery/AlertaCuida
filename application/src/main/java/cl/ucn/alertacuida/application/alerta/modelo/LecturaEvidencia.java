package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.telemetria.TipoLectura;

/**
 * Una lectura de la evidencia de una alerta, leída para compararla con los umbrales vigentes.
 *
 * @param tipo  FC o SPO2
 * @param valor en latidos por minuto o en %
 */
public record LecturaEvidencia(TipoLectura tipo, double valor) {
}
