package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.List;

/**
 * Un WhatsApp de plantilla listo para enviar por la Cloud API de Meta.
 *
 * @param telefono   a quién, en formato +569…
 * @param plantilla  el nombre exacto de la plantilla en Meta
 * @param idioma     su código de idioma (ej. "es_CL")
 * @param ubicacion  coordenadas para {@code HEADER: LOCATION}; null si la plantilla no lleva mapa
 * @param parametros valores del cuerpo, con su {@code parameter_name} si es {@code NAMED}
 * @param botones    el payload de cada botón {@code quick_reply}, en orden
 */
public record MensajeWhatsapp(String telefono, String plantilla, String idioma, UbicacionWhatsapp ubicacion,
		List<ParametroPlantilla> parametros, List<String> botones) {

	/** Copia las listas para que no cambien después. */
	public MensajeWhatsapp {
		parametros = List.copyOf(parametros);
		botones = List.copyOf(botones);
	}

}
