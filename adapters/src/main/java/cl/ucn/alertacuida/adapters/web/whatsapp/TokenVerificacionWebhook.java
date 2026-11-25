package cl.ucn.alertacuida.adapters.web.whatsapp;

/**
 * El Verify token que se escribe en el panel de Meta al configurar el webhook (WHATSAPP_VERIFY_TOKEN). Lo
 * inventa quien configura; Meta lo manda de vuelta en la verificación inicial.
 *
 * @param valor vacío si WhatsApp no está configurado: entonces ninguna verificación pasa
 */
public record TokenVerificacionWebhook(String valor) {

	/** Sin el valor: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "TokenVerificacionWebhook[]";
	}

}
