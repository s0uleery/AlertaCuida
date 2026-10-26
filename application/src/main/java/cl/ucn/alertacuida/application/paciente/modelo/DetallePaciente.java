package cl.ucn.alertacuida.application.paciente.modelo;

import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionHistorial;
import java.util.List;

/**
 * Ficha de un paciente con su historial de relojes.
 *
 * @param edad      en años, calculada hoy; vacía si no hay fecha de nacimiento
 * @param historial relojes que ha tenido, del más reciente al más antiguo; el primero sin fecha de fin
 *                  es el vigente
 */
public record DetallePaciente(Paciente paciente, Integer edad, List<AsignacionHistorial> historial) {

	public DetallePaciente {
		historial = List.copyOf(historial);
	}

}
