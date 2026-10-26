package cl.ucn.alertacuida.adapters.web.condicion;

import cl.ucn.alertacuida.domain.paciente.TipoCondicion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/pacientes/{id}/condiciones}. Aquí solo se revisa la forma; la
 * regla de no repetir está en el caso de uso.
 *
 * @param tipo        ENFERMEDAD o SITUACION
 * @param descripcion texto libre, por ejemplo "EPOC" o "Movilidad reducida"
 */
public record SolicitudCondicion(@NotNull TipoCondicion tipo, @NotBlank @Size(max = 200) String descripcion) {
}
