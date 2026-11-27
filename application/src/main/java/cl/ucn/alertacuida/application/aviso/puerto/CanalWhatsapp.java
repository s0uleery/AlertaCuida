package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.aviso.error.CanalNoConfigurado;
import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeSesionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;

/** Por donde salen los WhatsApp: la Cloud API de Meta, el simulador de contactos o, sin configurar, ninguno. */
@FunctionalInterface
public interface CanalWhatsapp {

	/**
	 * Envía una plantilla aprobada de Meta para iniciar el aviso de una alerta.
	 *
	 * @param aviso lo que se envía, con los datos ya descifrados
	 * @return el identificador del mensaje (wamid), para seguir su entrega y correlacionar botones
	 * @throws EnvioFallido       si no se pudo enviar y vale la pena reintentar
	 * @throws CanalNoConfigurado si el canal no está configurado: no se reintenta
	 */
	String enviar(WhatsappPendiente aviso);

	/**
	 * Envía un mensaje de sesión (texto o botones interactivos) dentro de la ventana de atención de 24 horas.
	 *
	 * @param mensaje texto o mensaje interactivo de hasta 3 botones
	 * @return el identificador del mensaje (wamid) devuelto por Meta
	 * @throws EnvioFallido       si no se pudo enviar y vale la pena reintentar
	 * @throws CanalNoConfigurado si el canal no está configurado
	 */
	default String enviarSesion(MensajeSesionWhatsapp mensaje) {
		throw new CanalNoConfigurado("El canal de WhatsApp no soporta mensajes de sesión");
	}

}
