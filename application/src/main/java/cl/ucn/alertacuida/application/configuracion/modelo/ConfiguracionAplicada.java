package cl.ucn.alertacuida.application.configuracion.modelo;

import java.time.Instant;

/**
 * Qué configuración aplica el reloj de un paciente, para el dashboard.
 *
 * @param estado               al día, pendiente, sin sincronizar o sin reloj
 * @param versionVigente       la que calcula hoy el servidor
 * @param versionReloj         la que informó el reloj en su última sincronización; vacía si no ha
 *                             informado ninguna
 * @param aplicadaDesde        desde qué sincronización informa esa versión sin interrupción; vacía si
 *                             no ha sincronizado
 * @param ultimaSincronizacion cuándo llegó su último lote; vacía si no ha sincronizado
 */
public record ConfiguracionAplicada(EstadoConfiguracion estado, String versionVigente, String versionReloj,
		Instant aplicadaDesde, Instant ultimaSincronizacion) {
}
