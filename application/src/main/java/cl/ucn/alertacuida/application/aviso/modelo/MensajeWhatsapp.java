package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.List;

/**
 * Un WhatsApp listo para enviar: la plantilla aprobada y los valores que la rellenan.
 *
 * @param telefono  a quién, en formato +569…
 * @param plantilla el nombre de la plantilla en Meta
 * @param idioma    su código de idioma
 * @param valores   el texto de {{1}}, {{2}}…, en orden
 * @param botones   el payload de cada botón de respuesta rápida, en orden; Meta lo devuelve al presionarlo
 */
public record MensajeWhatsapp(String telefono, String plantilla, String idioma, List<String> valores,
		List<String> botones) {

	/** Copia las listas para que no cambien después. */
	public MensajeWhatsapp {
		valores = List.copyOf(valores);
		botones = List.copyOf(botones);
	}

}
