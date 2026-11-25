package cl.ucn.alertacuida.application.aviso.puerto;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Estados de entrega de los WhatsApp enviados, buscados por el wamid que devolvió Meta (registro_intento.id_externo).
 * Cada fecha se guarda solo si estaba vacía: un webhook repetido no cambia nada y uno desordenado no retrocede.
 */
public interface EstadosWhatsapp {

	/** @return si guardó la entrega; false si el mensaje no es nuestro o ya la tenía */
	boolean registrarEntrega(String idExterno, Instant fecha);

	/**
	 * Guarda la lectura y, si faltaba, también la entrega: Meta puede mandar "read" sin "delivered".
	 *
	 * @return si guardó la lectura
	 */
	boolean registrarLectura(String idExterno, Instant fecha);

	/**
	 * Deja el WhatsApp fallido, solo si no había fallado ni tiene respuesta. Un fallido dispara el correo de respaldo.
	 *
	 * @param motivo el código y el título del error de Meta
	 * @return si lo dejó fallido
	 */
	boolean registrarFallo(String idExterno, String motivo, Instant fecha);

	/** @return el aviso de ese wamid, si es un WhatsApp nuestro */
	Optional<UUID> intentoPorIdExterno(String idExterno);

}
