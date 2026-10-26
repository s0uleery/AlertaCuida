package cl.ucn.alertacuida.application.reloj.modelo;

import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import java.time.LocalDate;

/**
 * Datos de un reloj del inventario, tal como se escriben al registrarlo o editarlo.
 *
 * @param identificadorReloj   ID único que informa el reloj
 * @param modelo               opcional, ej. Galaxy Watch
 * @param iccid                opcional: identificador físico de la SIM LTE, no cambia
 * @param numeroSim            opcional: número de la SIM, puede cambiar
 * @param fechaVencimientoPlan opcional: vencido, el reloj no envía alertas
 */
public record DatosReloj(String identificadorReloj, String modelo, String iccid, String numeroSim,
		LocalDate fechaVencimientoPlan, EstadoReloj estado) {
}
