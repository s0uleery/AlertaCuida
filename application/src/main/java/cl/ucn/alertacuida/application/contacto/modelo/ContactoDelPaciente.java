package cl.ucn.alertacuida.application.contacto.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un contacto de la lista de un paciente: la persona y su vínculo con el paciente.
 *
 * @param id             identificador del vínculo (contacto_paciente.id_contacto_paciente); es el
 *                       que conoce el reloj
 * @param persona        la persona, con sus medios de contacto y su opt-in
 * @param vinculo        tipo, parentesco, si es cuidador principal y horario
 * @param ordenes        en qué gravedades recibe avisos y en qué lugar de cada una (1 = primero); vacío si está
 *                       desactivado. Una alerta recorre solo los contactos de su gravedad
 * @param activo         false si se desactivó (no se borra: sus avisos quedan en el historial)
 * @param otrosPacientes de cuántos otros pacientes es contacto activo la misma persona
 */
public record ContactoDelPaciente(UUID id, Persona persona, Vinculo vinculo, Map<Severidad, Integer> ordenes,
		boolean activo, int otrosPacientes) {

	public ContactoDelPaciente {
		Map<Severidad, Integer> copia = new EnumMap<>(Severidad.class);
		copia.putAll(ordenes);
		ordenes = Collections.unmodifiableMap(copia);
	}

	/** @return su lugar en la lista de esa gravedad (1 = primero), o null si no está en ella */
	public Integer orden(Severidad gravedad) {
		return ordenes.get(gravedad);
	}

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
