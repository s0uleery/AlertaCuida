package cl.ucn.alertacuida.application.aviso.modelo;

import java.time.Instant;

/** Lo que interesa de un webhook de Meta, ya leído del JSON: un estado de entrega o un botón presionado. */
public sealed interface EventoWhatsapp {

	/**
	 * Un estado de un WhatsApp enviado ({@code statuses[]}).
	 *
	 * @param idExterno el wamid del mensaje ({@code statuses[].id})
	 * @param estado    "sent", "delivered", "read" o "failed", tal como lo manda Meta
	 * @param fecha     cuándo ocurrió, según Meta
	 * @param motivo    en un "failed", el código y el título del error de Meta; null en los demás
	 */
	record Estado(String idExterno, String estado, Instant fecha, String motivo) implements EventoWhatsapp {
	}

	/**
	 * Un botón de respuesta rápida presionado ({@code messages[]} de tipo "button" o "interactive").
	 *
	 * @param idMensajeOriginal el wamid del mensaje que tenía el botón ({@code messages[].context.id})
	 * @param valor             el payload del botón, o su id si es interactivo
	 */
	record Boton(String idMensajeOriginal, String valor) implements EventoWhatsapp {
	}

}
