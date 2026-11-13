package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import java.time.Instant;
import java.util.List;

/** Los WhatsApp de la bandeja de salida que toca enviar (registro_intento por el canal WHATSAPP). */
@FunctionalInterface
public interface BandejaWhatsapp {

	/** @return los WhatsApp cuyo próximo envío ya llegó, el más antiguo primero */
	List<WhatsappPendiente> pendientes(Instant ahora);

}
