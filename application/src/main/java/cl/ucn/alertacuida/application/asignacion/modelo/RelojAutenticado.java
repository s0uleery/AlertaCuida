package cl.ucn.alertacuida.application.asignacion.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Quién llama a la API del reloj: el vínculo vigente cuyo token vino en la petición. Todo lo que
 * envía el reloj se guarda bajo esta asignación; el reloj nunca dice de qué paciente es.
 *
 * @param inicio desde cuándo está vigente: las lecturas anteriores no son de este paciente
 */
public record RelojAutenticado(UUID idAsignacion, UUID idDispositivo, UUID idPaciente, Instant inicio) {
}
