package cl.ucn.alertacuida.domain.reloj;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Lo que se sabe de un reloj para su semáforo. Todo sale de lo que el reloj ya envía o de lo que está registrado;
 * el servidor no lo consulta.
 *
 * @param vinculado              si alguien lo usa ahora
 * @param inicioVinculo          desde cuándo; null si no está vinculado
 * @param ultimaSincronizacion   cuándo llegó su última sincronización del vínculo vigente; null si ninguna
 * @param nivelBateria           porcentaje que informó; null si no lo informó
 * @param relojPuesto            si informó que estaba puesto; null si no lo informó
 * @param versionApp             versión de su app; null si no la informó
 * @param configuracionPendiente si todavía no aplica la configuración vigente del paciente
 * @param alertaTecnicaAbierta   si tiene una alerta técnica sin cerrar
 * @param versionMasNueva        la versión de app más nueva que informa algún reloj; null si ninguno
 * @param vencimientoPlan        último día del plan de datos de su SIM; null si no se registró
 */
public record DatosSemaforo(boolean vinculado, Instant inicioVinculo, Instant ultimaSincronizacion,
		Integer nivelBateria, Boolean relojPuesto, String versionApp, boolean configuracionPendiente,
		boolean alertaTecnicaAbierta, String versionMasNueva, LocalDate vencimientoPlan) {
}
