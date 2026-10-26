package cl.ucn.alertacuida.application.contacto.modelo;

import cl.ucn.alertacuida.domain.contacto.Canal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un contacto de la lista de un paciente: la persona y su vínculo con el paciente.
 *
 * @param id             identificador del vínculo (contacto_paciente.id_contacto_paciente); es el
 *                       que conoce el reloj
 * @param persona        la persona, con sus medios de contacto y su opt-in
 * @param vinculo        tipo, parentesco, si es cuidador principal y horario
 * @param prioridad      orden en que el reloj avisa (1 = primero), único entre los activos
 * @param activo         false si se desactivó (no se borra: sus avisos quedan en el historial)
 * @param otrosPacientes de cuántos otros pacientes es contacto activo la misma persona
 */
public record ContactoDelPaciente(UUID id, Persona persona, Vinculo vinculo, int prioridad, boolean activo,
		int otrosPacientes) {

	/** @return los canales que el servidor puede usar hoy: WhatsApp solo con opt-in vigente */
	public List<Canal> canales() {
		List<Canal> canales = new ArrayList<>();
		if (persona.optInVigente()) {
			canales.add(Canal.WHATSAPP);
		}
		if (persona.correo() != null) {
			canales.add(Canal.CORREO);
		}
		return canales;
	}

	/** @return si el reloj puede pedir avisos a este contacto: activo y con algún canal usable */
	public boolean disponible() {
		return activo && !canales().isEmpty();
	}

}
