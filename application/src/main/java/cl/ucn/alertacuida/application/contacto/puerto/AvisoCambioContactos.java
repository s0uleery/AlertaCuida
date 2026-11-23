package cl.ucn.alertacuida.application.contacto.puerto;

import java.util.UUID;

/** Se le informa cada vez que cambia la lista de contactos de un paciente. */
@FunctionalInterface
public interface AvisoCambioContactos {

	/** No debe lanzar excepciones: el cambio ya quedó guardado. */
	void listaCambiada(UUID idPaciente);

}
