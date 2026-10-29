package cl.ucn.alertacuida.application.telemetria.modelo;

import java.time.Instant;

/**
 * La versión de configuración que informó el reloj en su último lote.
 *
 * @param version la informada; vacía si el reloj aún no aplica ninguna
 * @param ultima  cuándo llegó el último lote
 * @param desde   cuándo llegó el primer lote de la racha actual con esa misma versión
 */
public record VersionInformada(String version, Instant ultima, Instant desde) {
}
