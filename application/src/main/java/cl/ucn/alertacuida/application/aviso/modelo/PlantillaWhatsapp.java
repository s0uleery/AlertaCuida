package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.List;

/**
 * Una plantilla aprobada en Meta, tal como hay que pedirla al enviar. Se configura en application.properties
 * (alertacuida.whatsapp.plantillas.*) con lo que entrega Meta al listar las plantillas aprobadas.
 *
 * @param nombre    el nombre exacto en Meta
 * @param idioma    el código de idioma con que se aprobó (ej. "es" o "es_CL")
 * @param variables qué va en {{1}}, {{2}}…, en orden: "quien", "folio" o "gravedad"
 * @param botones   el payload de cada botón de respuesta rápida, en orden: "VOY_EN_CAMINO" o "NO_PUEDO";
 *                  vacío si la plantilla no tiene botones
 */
public record PlantillaWhatsapp(String nombre, String idioma, List<String> variables, List<String> botones) {

	/** Normaliza las listas vacías: una propiedad sin valor llega como null. */
	public PlantillaWhatsapp {
		variables = variables == null ? List.of() : List.copyOf(variables);
		botones = botones == null ? List.of() : List.copyOf(botones);
	}

}
