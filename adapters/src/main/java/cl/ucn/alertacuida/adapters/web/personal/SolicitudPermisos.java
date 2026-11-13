package cl.ucn.alertacuida.adapters.web.personal;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Cuerpo de {@code PUT /api/roles/{codigo}/permisos}.
 *
 * @param permisos todos los permisos que tendrá el rol, como "MODULO:ACCION"; vacío lo deja sin ninguno
 */
public record SolicitudPermisos(@NotNull List<String> permisos) {
}
