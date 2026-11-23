package cl.ucn.alertacuida.adapters.whatsapp;

import cl.ucn.alertacuida.application.aviso.error.CanalNoConfigurado;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.CanalWhatsapp;

/**
 * Canal de WhatsApp mientras no estén las credenciales de Meta: no envía nada. Cada aviso queda fallido de
 * inmediato y sale el correo de respaldo (decisión del 13-11-2026).
 */
public class CanalWhatsappNoConfigurado implements CanalWhatsapp {

	/** Texto que queda en motivo_fallo y ve el equipo en la línea de tiempo. */
	static final String MOTIVO = "WhatsApp aún no está configurado: se envía el correo de respaldo";

	/** @throws CanalNoConfigurado siempre */
	@Override
	public String enviar(WhatsappPendiente aviso) {
		throw new CanalNoConfigurado(MOTIVO);
	}

}
