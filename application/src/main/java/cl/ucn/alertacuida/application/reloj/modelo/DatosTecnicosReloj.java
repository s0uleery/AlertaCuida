package cl.ucn.alertacuida.application.reloj.modelo;

import cl.ucn.alertacuida.domain.telemetria.TipoConexion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo que la base sabe de un reloj para su estado técnico: su vínculo vigente, su última sincronización en ese
 * vínculo y si tiene alguna alerta técnica abierta.
 *
 * @param idPaciente             quién lo usa; null si nadie
 * @param inicioVinculo          desde cuándo; null si nadie lo usa
 * @param vencimientoPlan        último día del plan de datos de su SIM; null si no se registró
 * @param ultimaSincronizacion   cuándo llegó su última sincronización del vínculo vigente; null si ninguna
 * @param nivelBateria           lo que informó; null si no lo informó
 * @param relojPuesto            lo que informó; null si no lo informó
 * @param tipoConexion           lo que informó; null si no lo informó
 * @param versionApp             lo que informó; null si no lo informó
 * @param versionConfiguracion   la configuración que informó tener; null si no la informó
 * @param alertaTecnicaAbierta   si tiene una alerta técnica real sin cerrar en el vínculo vigente
 */
public record DatosTecnicosReloj(UUID idDispositivo, UUID idPaciente, Instant inicioVinculo,
		LocalDate vencimientoPlan, Instant ultimaSincronizacion, Integer nivelBateria, Boolean relojPuesto,
		TipoConexion tipoConexion, String versionApp, String versionConfiguracion, boolean alertaTecnicaAbierta) {
}
