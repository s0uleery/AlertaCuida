package cl.ucn.alertacuida.configuracion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Calendario hábil (alertacuida.calendario.* en calendario.yml). Llega como texto y lo
 * convierte {@link ConfiguracionCalendario}, para que un error de escritura diga qué línea revisar.
 *
 * @param horario  tramo de cada día hábil, con el día en minúsculas ("lunes"); los que faltan no son hábiles
 * @param feriados nombre de cada feriado, con la fecha (AAAA-MM-DD) como clave
 */
@Validated
@ConfigurationProperties("alertacuida.calendario")
public record PropiedadesCalendario(@NotEmpty Map<String, @Valid Tramo> horario,
		@NotNull Map<String, String> feriados) {

	/** Horario de un día, en hora de Chile ("08:30"): desde el inicio, incluido, hasta el fin, sin incluirlo. */
	public record Tramo(@NotBlank String inicio, @NotBlank String fin) {
	}

}
