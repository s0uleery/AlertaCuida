package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.List;

/**
 * Una plantilla aprobada en Meta, tal como hay que pedirla al enviar. Se configura en application.properties
 * (alertacuida.whatsapp.plantillas.*) con lo que entrega Meta al listar las plantillas aprobadas.
 *
 * @param nombre       el nombre exacto en Meta (puede estar vacío en plantillas opcionales como las sin mapa)
 * @param idioma       el código de idioma con que se aprobó (ej. "es_CL")
 * @param nombrada     si la plantilla usa {@code parameter_format: NAMED} (envía {@code parameter_name})
 * @param conUbicacion si la plantilla exige un {@code HEADER} de tipo {@code LOCATION}
 * @param variables    qué va en cada parámetro del cuerpo, en orden
 * @param botones      el payload de cada botón de respuesta rápida, en orden; vacío si no tiene
 */
public record PlantillaWhatsapp(String nombre, String idioma, boolean nombrada, boolean conUbicacion,
		List<String> variables, List<String> botones) {

	/** Normaliza las listas vacías: una propiedad sin valor llega como null. */
	public PlantillaWhatsapp {
		variables = variables == null ? List.of() : List.copyOf(variables);
		botones = botones == null ? List.of() : List.copyOf(botones);
	}

	/** @return si esta plantilla tiene nombre configurado */
	public boolean configurada() {
		return nombre != null && !nombre.isBlank();
	}

}
