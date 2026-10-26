package cl.ucn.alertacuida.application.paciente.modelo;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un paciente guardado.
 *
 * @param datos     su identidad, con el RUN normalizado
 * @param fechaBaja vacía mientras está activo en el programa
 */
public record Paciente(UUID id, DatosPaciente datos, LocalDate fechaBaja) {

	/** @return si sigue en el programa */
	public boolean activo() {
		return fechaBaja == null;
	}

}
