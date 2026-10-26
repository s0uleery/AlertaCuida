package cl.ucn.alertacuida.application.clinica.modelo;

import cl.ucn.alertacuida.domain.clinica.Umbral;
import java.time.Instant;

/**
 * El umbral que rige para una variable de un paciente, sea propio o el base.
 *
 * @param umbral los límites que usa el reloj
 * @param propio si es un ajuste del paciente (false = rige el umbral base)
 * @param desde  desde cuándo rige el ajuste propio; vacío si rige el base sin cambios
 */
public record UmbralVigente(Umbral umbral, boolean propio, Instant desde) {
}
