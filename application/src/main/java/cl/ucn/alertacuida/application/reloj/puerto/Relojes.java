package cl.ucn.alertacuida.application.reloj.puerto;

import cl.ucn.alertacuida.application.reloj.modelo.DatosReloj;
import cl.ucn.alertacuida.application.reloj.modelo.Reloj;
import cl.ucn.alertacuida.application.reloj.modelo.RelojConPaciente;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Inventario de relojes (tabla smartwatch). Los relojes no se borran: se retiran. */
public interface Relojes {

	/**
	 * @param excepto reloj que no cuenta (el que se está editando), o vacío
	 * @return si otro reloj ya tiene ese identificador
	 */
	boolean existeIdentificador(String identificadorReloj, UUID excepto);

	/**
	 * @return el identificador del reloj registrado
	 * @throws cl.ucn.alertacuida.application.comun.error.Conflicto si el ICCID o el número de SIM ya
	 *                                                              son de otro reloj
	 */
	UUID crear(DatosReloj datos);

	/**
	 * Reemplaza los datos del reloj.
	 *
	 * @throws cl.ucn.alertacuida.application.comun.error.Conflicto si el ICCID o el número de SIM ya
	 *                                                              son de otro reloj
	 */
	void actualizar(UUID id, DatosReloj datos);

	/** Cambia solo el estado físico (lo usa el reemplazo para dejar el reloj anterior en reparación). */
	void cambiarEstado(UUID id, EstadoReloj estado);

	/** @return el reloj, en cualquier estado */
	Optional<Reloj> buscar(UUID id);

	/** @return todos los relojes con el paciente que los usa, ordenados por identificador */
	List<RelojConPaciente> listar();

}
