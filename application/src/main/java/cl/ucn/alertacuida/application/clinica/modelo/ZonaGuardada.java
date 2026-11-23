package cl.ucn.alertacuida.application.clinica.modelo;

import cl.ucn.alertacuida.domain.clinica.ZonaSegura;
import java.time.Instant;

/**
 * La fila vigente de la tabla zona_segura de un paciente.
 *
 * @param zona         centro, radio y severidad
 * @param vigenteDesde desde cuándo rige
 */
public record ZonaGuardada(ZonaSegura zona, Instant vigenteDesde) {
}
