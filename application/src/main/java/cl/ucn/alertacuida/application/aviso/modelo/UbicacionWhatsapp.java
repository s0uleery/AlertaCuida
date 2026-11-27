package cl.ucn.alertacuida.application.aviso.modelo;

/**
 * Coordenadas para el encabezado {@code HEADER: LOCATION} de las plantillas de Meta.
 *
 * @param latitud   grados decimales
 * @param longitud  grados decimales
 * @param nombre    etiqueta visible del punto en el mapa de WhatsApp
 * @param direccion texto de referencia mostrado bajo el nombre
 */
public record UbicacionWhatsapp(double latitud, double longitud, String nombre, String direccion) {

	/** Normaliza nombre y dirección para evitar valores nulos en el JSON de Meta. */
	public UbicacionWhatsapp {
		nombre = nombre == null || nombre.isBlank() ? "Ubicación de alerta" : nombre.strip();
		direccion = direccion == null || direccion.isBlank() ? "Sin dirección registrada" : direccion.strip();
	}

}
