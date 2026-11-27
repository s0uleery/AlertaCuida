package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.Objects;

/**
 * Un parámetro de texto del cuerpo de una plantilla de Meta. Si {@code nombre} tiene valor, se serializa en
 * formato {@code NAMED} ({@code parameter_name}); si es null, en formato {@code POSITIONAL}.
 *
 * @param nombre nombre exacto de la variable en Meta (ej. "id_alerta"), o null si es posicional
 * @param valor  texto que reemplaza la variable (nunca vacío: Meta rechaza cadenas vacías)
 */
public record ParametroPlantilla(String nombre, String valor) {

	/** Valida que el valor no venga vacío. */
	public ParametroPlantilla {
		Objects.requireNonNull(valor, "valor");
		if (valor.isBlank()) {
			throw new IllegalArgumentException("El valor de un parámetro de plantilla no puede estar vacío");
		}
		nombre = nombre == null || nombre.isBlank() ? null : nombre.strip();
	}

}
