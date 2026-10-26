package cl.ucn.alertacuida.application.condicion.modelo;

import cl.ucn.alertacuida.domain.paciente.TipoCondicion;
import java.util.UUID;

/**
 * Una enfermedad o situación de un paciente (tabla condicion_paciente).
 *
 * @param id          identificador de la condición
 * @param tipo        enfermedad o situación
 * @param descripcion texto libre, por ejemplo "EPOC" o "Movilidad reducida"
 */
public record Condicion(UUID id, TipoCondicion tipo, String descripcion) {
}
