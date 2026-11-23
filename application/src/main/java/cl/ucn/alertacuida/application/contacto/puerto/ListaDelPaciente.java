package cl.ucn.alertacuida.application.contacto.puerto;

import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import java.util.List;
import java.util.UUID;

/**
 * Solo la lista de contactos de un paciente, ya descifrada. Va aparte de {@link Contactos} para que quien
 * solo la lee (por ejemplo, el escalamiento) no dependa de las altas y cambios.
 */
@FunctionalInterface
public interface ListaDelPaciente {

	/** @return todos los contactos del paciente, activos y desactivados, sin orden definido */
	List<ContactoDelPaciente> delPaciente(UUID idPaciente);

}
