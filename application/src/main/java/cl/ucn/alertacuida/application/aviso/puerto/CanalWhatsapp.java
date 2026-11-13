package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.aviso.error.CanalNoConfigurado;
import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;

/** Por donde salen los WhatsApp: la Cloud API de Meta, el simulador de contactos o, sin configurar, ninguno. */
@FunctionalInterface
public interface CanalWhatsapp {

	/**
	 * @param aviso lo que se envía, con el número ya descifrado
	 * @return el identificador del mensaje (wamid), para seguir su entrega
	 * @throws EnvioFallido       si no se pudo enviar y vale la pena reintentar
	 * @throws CanalNoConfigurado si el canal no está configurado: no se reintenta
	 */
	String enviar(WhatsappPendiente aviso);

}
