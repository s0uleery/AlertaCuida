package cl.ucn.alertacuida.application.paciente;

import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.paciente.modelo.DetallePaciente;
import cl.ucn.alertacuida.application.paciente.modelo.Paciente;
import cl.ucn.alertacuida.application.paciente.modelo.ResumenPaciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lista y ficha de pacientes. La edad no se guarda (se volvería incorrecta): se calcula aquí.
 *
 * <p>La lista no se filtra en la base: los nombres irán cifrados (1.6.3) y no se podrán buscar con
 * SQL. Con los pacientes del piloto, el dashboard filtra en el navegador.
 */
public class ConsultarPacientes {

	private final Pacientes pacientes;
	private final Asignaciones asignaciones;
	private final Clock reloj;

	public ConsultarPacientes(Pacientes pacientes, Asignaciones asignaciones, Clock reloj) {
		this.pacientes = pacientes;
		this.asignaciones = asignaciones;
		this.reloj = reloj;
	}

	/**
	 * @param incluirBajas si también se listan los que dejaron el programa
	 * @return los pacientes con su edad y su reloj actual, ordenados por apellidos
	 */
	public List<ResumenPaciente> listar(boolean incluirBajas) {
		LocalDate hoy = ZonaHoraria.hoy(reloj);
		Map<UUID, String> relojes = asignaciones.relojesVigentesPorPaciente();
		return pacientes.listar(incluirBajas).stream()
				.map(paciente -> new ResumenPaciente(paciente, edad(paciente, hoy), relojes.get(paciente.id())))
				.toList();
	}

	/**
	 * @param id paciente
	 * @return su identidad, edad e historial de relojes
	 * @throws NoEncontrado si no existe
	 */
	public DetallePaciente detalle(UUID id) {
		Paciente paciente = pacientes.buscar(id).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
		return new DetallePaciente(paciente, edad(paciente, ZonaHoraria.hoy(reloj)),
				asignaciones.historialDelPaciente(id));
	}

	/** @return años cumplidos hoy, o null si no hay fecha de nacimiento */
	private static Integer edad(Paciente paciente, LocalDate hoy) {
		LocalDate nacimiento = paciente.datos().fechaNacimiento();
		return nacimiento == null ? null : Period.between(nacimiento, hoy).getYears();
	}

}
