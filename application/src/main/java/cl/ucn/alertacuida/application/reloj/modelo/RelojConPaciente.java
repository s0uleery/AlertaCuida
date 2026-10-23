package cl.ucn.alertacuida.application.reloj.modelo;

import java.util.UUID;

/**
 * Fila del inventario de relojes.
 *
 * @param idPaciente     paciente que lo usa ahora, o vacío si está libre
 * @param nombrePaciente nombres y apellidos de ese paciente, para mostrar
 */
public record RelojConPaciente(Reloj reloj, UUID idPaciente, String nombrePaciente) {
}
