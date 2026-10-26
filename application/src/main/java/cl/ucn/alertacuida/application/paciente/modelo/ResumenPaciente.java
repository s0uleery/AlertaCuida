package cl.ucn.alertacuida.application.paciente.modelo;

/**
 * Fila de la lista de pacientes.
 *
 * @param edad        en años, calculada hoy; vacía si no hay fecha de nacimiento
 * @param relojActual identificador del reloj vinculado, o vacío si no tiene
 */
public record ResumenPaciente(Paciente paciente, Integer edad, String relojActual) {
}
