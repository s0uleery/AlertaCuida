package cl.ucn.alertacuida.application.condicion.puerto;

import cl.ucn.alertacuida.application.condicion.modelo.Condicion;
import cl.ucn.alertacuida.domain.paciente.TipoCondicion;
import java.util.List;
import java.util.UUID;

/** Enfermedades y situaciones guardadas (tabla condicion_paciente). La descripción se cifrará en el adaptador. */
public interface Condiciones {

	/** @return las condiciones del paciente, sin orden definido */
	List<Condicion> delPaciente(UUID idPaciente);

	/** @return el identificador de la condición creada */
	UUID crear(UUID idPaciente, TipoCondicion tipo, String descripcion);

	/**
	 * Borra la condición solo si es de ese paciente.
	 *
	 * @return si se borró (false si no existe o es de otro paciente)
	 */
	boolean eliminar(UUID idPaciente, UUID idCondicion);

}
