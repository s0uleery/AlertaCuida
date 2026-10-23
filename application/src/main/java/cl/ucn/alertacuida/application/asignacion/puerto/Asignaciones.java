package cl.ucn.alertacuida.application.asignacion.puerto;

import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionHistorial;
import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionVigente;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Vínculos reloj-paciente (tabla asignacion_reloj). Nunca se borran: se cierran con una fecha de fin.
 * La base impide que dos vínculos de un mismo reloj o de un mismo paciente se crucen en el tiempo.
 */
public interface Asignaciones {

	/** @return el vínculo vigente del paciente, si tiene reloj */
	Optional<AsignacionVigente> vigenteDelPaciente(UUID idPaciente);

	/** @return el vínculo vigente del reloj, si está con algún paciente */
	Optional<AsignacionVigente> vigenteDelReloj(UUID idDispositivo);

	/** @return por cada paciente con reloj, el identificador de ese reloj (para la lista de pacientes) */
	Map<UUID, String> relojesVigentesPorPaciente();

	/** @return los relojes que ha tenido el paciente, del más reciente al más antiguo */
	List<AsignacionHistorial> historialDelPaciente(UUID idPaciente);

	/**
	 * Crea un vínculo vigente.
	 *
	 * @param hashToken hash del token del reloj, nunca el token
	 * @return el identificador del vínculo
	 * @throws cl.ucn.alertacuida.application.comun.error.Conflicto si otra petición vinculó antes el reloj
	 *                                                              o el paciente
	 */
	UUID abrir(UUID idDispositivo, UUID idPaciente, Instant inicio, String hashToken);

	/** Cierra el vínculo: desde {@code fin}, su token deja de servir. */
	void cerrar(UUID idAsignacion, Instant fin);

	/** Reemplaza el token del vínculo: el anterior deja de servir. */
	void cambiarToken(UUID idAsignacion, String hashToken);

}
