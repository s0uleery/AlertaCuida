package cl.ucn.alertacuida.application.clinica.modelo;

import java.time.Instant;

/**
 * La fila vigente de la tabla configuracion de un paciente.
 *
 * @param ajustes      módulos y opciones
 * @param vigenteDesde desde cuándo rige
 */
public record ConfiguracionGuardada(AjustesClinicos ajustes, Instant vigenteDesde) {
}
