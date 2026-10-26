package cl.ucn.alertacuida.application.contacto.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Una persona o institución guardada una sola vez (tabla contacto), aunque sea contacto de varios
 * pacientes. El opt-in de WhatsApp es de la persona, porque Meta lo registra por número.
 *
 * @param id               identificador (contacto.id_contacto)
 * @param nombre           nombre, ya descifrado
 * @param whatsapp         número normalizado, o vacío
 * @param correo           correo normalizado, o vacío
 * @param fechaOptIn       cuándo aceptó recibir WhatsApp, o vacío
 * @param fechaRevocacion  cuándo lo revocó, o vacío
 */
public record Persona(UUID id, String nombre, String whatsapp, String correo, Instant fechaOptIn,
		Instant fechaRevocacion) {

	/** @return si se le puede enviar WhatsApp: aceptó y no lo ha revocado */
	public boolean optInVigente() {
		return whatsapp != null && fechaOptIn != null && fechaRevocacion == null;
	}

}
