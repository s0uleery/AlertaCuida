package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.vigilancia.modelo.ParametrosVigilancia;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Reglas de ausencia de los relojes (alertacuida.vigilancia.* en application.properties).
 *
 * @param intervalo     cada cuánto corre el vigilante; lo lee directamente la tarea programada
 * @param silencio      tiempo sin sincronizar desde el cual hay silencio de datos
 * @param bateriaMinima porcentaje desde el cual la batería ya no se considera baja
 * @param horaInicioDia hora de Chile (incluida) en que parte la revisión de reloj no puesto
 * @param horaFinDia    hora de Chile (excluida) en que termina esa revisión
 */
@Validated
@ConfigurationProperties("alertacuida.vigilancia")
public record PropiedadesVigilancia(@NotNull Duration intervalo, @NotNull Duration silencio,
		@Min(1) @Max(100) int bateriaMinima, @Min(0) @Max(23) int horaInicioDia, @Min(1) @Max(24) int horaFinDia) {

	/** @return los valores para las reglas */
	public ParametrosVigilancia parametros() {
		return new ParametrosVigilancia(silencio, bateriaMinima, horaInicioDia, horaFinDia);
	}

}
