package cl.ucn.alertacuida.application.asignacion.modelo;

import java.util.UUID;

/** El vínculo que está en uso entre un reloj y un paciente (asignacion_reloj sin fecha de fin). */
public record AsignacionVigente(UUID idAsignacion, UUID idDispositivo, UUID idPaciente) {
}
