package cl.ucn.alertacuida.application.clinica.modelo;

import cl.ucn.alertacuida.domain.clinica.Umbral;
import java.time.Instant;

/**
 * La fila vigente de la tabla umbral de un paciente para una variable.
 *
 * @param umbral       los límites; vacío si la fila está vacía (se volvió al umbral base)
 * @param vigenteDesde desde cuándo rige
 */
public record UmbralGuardado(Umbral umbral, Instant vigenteDesde) {
}
