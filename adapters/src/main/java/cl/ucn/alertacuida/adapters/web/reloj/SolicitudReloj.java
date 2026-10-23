package cl.ucn.alertacuida.adapters.web.reloj;

import cl.ucn.alertacuida.application.reloj.modelo.DatosReloj;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/relojes} y {@code PUT /api/relojes/{id}}.
 *
 * @param identificadorReloj   ID único que informa el reloj
 * @param modelo               opcional
 * @param iccid                opcional: identificador de la SIM LTE
 * @param numeroSim            opcional
 * @param fechaVencimientoPlan opcional, como "2027-03-31"
 * @param estado               opcional al registrar (OPERATIVO por defecto)
 */
public record SolicitudReloj(
		@NotBlank @Size(max = 100) String identificadorReloj,
		@Size(max = 100) String modelo,
		@Size(max = 30) String iccid,
		@Size(max = 20) String numeroSim,
		LocalDate fechaVencimientoPlan,
		EstadoReloj estado) {

	/** @return los mismos datos en el formato del caso de uso */
	DatosReloj datos() {
		return new DatosReloj(identificadorReloj, modelo, iccid, numeroSim, fechaVencimientoPlan, estado);
	}

}
