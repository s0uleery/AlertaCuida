package cl.ucn.alertacuida.application.telemetria.modelo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lo que se responde al reloj por un lote.
 *
 * @param lecturasRepetidas     válidas, pero ya guardadas por un lote anterior
 * @param loteRepetido          el reloj reenvió un lote ya recibido: no se guardó nada nuevo
 * @param desfaseRelojSegundos  hora del reloj menos la del servidor, solo si supera 120 s; si no, null
 * @param configuracionNueva    si el reloj debe descargar la configuración
 * @param versionConfiguracion  la vigente en el servidor
 */
public record ResultadoSincronizacion(UUID idSincronizacion, Instant recepcion, int lecturasGuardadas,
		int lecturasRepetidas, List<LecturaRechazada> lecturasRechazadas, boolean loteRepetido,
		Long desfaseRelojSegundos, boolean configuracionNueva, String versionConfiguracion) {
}
