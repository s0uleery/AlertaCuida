package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.Objects;

/**
 * Un botón de respuesta rápida dentro de un mensaje interactivo de WhatsApp (ventana de 24 h). Meta exige que
 * el título tenga como máximo 20 caracteres y que el mensaje lleve entre 1 y 3 botones.
 *
 * @param id     payload devuelto por Meta en {@code interactive.button_reply.id}
 * @param titulo etiqueta visible en el botón (máximo 20 caracteres)
 */
public record BotonInteractivo(String id, String titulo) {

	/** Límite impuesto por la Cloud API de Meta para títulos de botones interactivos. */
	public static final int LARGO_MAXIMO_TITULO = 20;

	/** Valida id y largo del título al construir. */
	public BotonInteractivo {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(titulo, "titulo");
		id = id.strip();
		titulo = titulo.strip();
		if (id.isEmpty()) {
			throw new IllegalArgumentException("El id del botón interactivo no puede estar vacío");
		}
		if (titulo.isEmpty() || titulo.length() > LARGO_MAXIMO_TITULO) {
			throw new IllegalArgumentException("El título del botón interactivo debe tener entre 1 y "
					+ LARGO_MAXIMO_TITULO + " caracteres: " + titulo);
		}
	}

}
