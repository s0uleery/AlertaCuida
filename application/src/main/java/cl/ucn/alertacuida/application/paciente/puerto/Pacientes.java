package cl.ucn.alertacuida.application.paciente.puerto;

import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.paciente.modelo.Paciente;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pacientes guardados (tabla paciente). Los datos personales se cifrarán en el adaptador (1.6.3). */
public interface Pacientes {

	/**
	 * @param runHash HMAC del RUN normalizado
	 * @param excepto paciente que no cuenta (el que se está editando), o vacío
	 * @return si otro paciente ya tiene ese RUN
	 */
	boolean existeRunHash(String runHash, UUID excepto);

	/**
	 * @param datos   identidad con el RUN normalizado
	 * @param runHash HMAC del RUN, para buscar sin descifrar
	 * @return el identificador del paciente creado
	 */
	UUID crear(DatosPaciente datos, String runHash);

	/** Reemplaza la identidad del paciente. */
	void actualizar(UUID id, DatosPaciente datos, String runHash);

	/** @return el paciente, activo o dado de baja */
	Optional<Paciente> buscar(UUID id);

	/**
	 * @param incluirBajas si también se listan los que dejaron el programa
	 * @return los pacientes, ordenados por apellidos y nombres
	 */
	List<Paciente> listar(boolean incluirBajas);

	/** Registra la fecha en que el paciente dejó el programa. */
	void darDeBaja(UUID id, LocalDate fecha);

}
