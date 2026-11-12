package cl.ucn.alertacuida.application.alerta.modelo;

import java.util.UUID;

/**
 * La persona a quien corresponde una alerta.
 *
 * @param edad años cumplidos; null si no se conoce su fecha de nacimiento
 */
public record PacienteDeAlerta(UUID id, String nombres, String apellidos, Integer edad) {
}
