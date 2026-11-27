package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.List;
import java.util.UUID;

/**
 * Un mensaje de sesión encolado para salir dentro de la ventana de 24 h de Meta.
 *
 * @param idMensaje      su identificador en mensaje_whatsapp_saliente
 * @param idConversacion conversación a la que pertenece
 * @param telefono       número destino ya descifrado
 * @param cuerpo         texto del mensaje
 * @param botones        botones interactivos (vacío = texto simple)
 * @param reintentos     cuántos reintentos lleva
 */
public record MensajeSalientePendiente(UUID idMensaje, UUID idConversacion, String telefono, String cuerpo,
		List<BotonInteractivo> botones, int reintentos) {

	/** Copia la lista de botones para mantener inmutabilidad. */
	public MensajeSalientePendiente {
		botones = botones == null ? List.of() : List.copyOf(botones);
	}

}
