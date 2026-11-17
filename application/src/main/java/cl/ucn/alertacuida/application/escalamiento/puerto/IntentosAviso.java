package cl.ucn.alertacuida.application.escalamiento.puerto;

import cl.ucn.alertacuida.application.escalamiento.modelo.IntentoExistente;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Avisos a los contactos del paciente que pide el reloj (filas de registro_intento con
 * id_contacto_paciente). Las claves únicas de la tabla impiden repetir un envío.
 */
public interface IntentosAviso {

	/** @return el aviso con ese eventId, si ya existe */
	Optional<IntentoExistente> porEventId(UUID eventId);

	/**
	 * @param idContacto el vínculo con el paciente
	 * @return el aviso de esa alerta al mismo contacto, canal y ronda, si ya existe
	 */
	Optional<UUID> mismoDestino(UUID idAlerta, UUID idContacto, Canal canal, int ronda);

	/**
	 * Deja el aviso en la bandeja de salida, pendiente desde ahora.
	 *
	 * @return su identificador, o vacío si otra petición registró el mismo eventId o destino antes
	 */
	Optional<UUID> encolar(UUID eventId, UUID idAlerta, UUID idContacto, Canal canal, int ronda, Instant ahora);

}
