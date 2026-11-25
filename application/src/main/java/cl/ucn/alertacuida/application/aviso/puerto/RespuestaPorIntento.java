package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import java.util.UUID;

/**
 * Registra la respuesta a un aviso con las mismas reglas del enlace del correo (vale la primera; "voy" confirma
 * y avisa al reloj). La implementa {@code ResponderAviso.responderIntento}.
 */
@FunctionalInterface
public interface RespuestaPorIntento {

	/**
	 * @param idIntento el aviso al que responde
	 * @param respuesta el botón que presionó
	 */
	void responder(UUID idIntento, RespuestaContacto respuesta);

}
