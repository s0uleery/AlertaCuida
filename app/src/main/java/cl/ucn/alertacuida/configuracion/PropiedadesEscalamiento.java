package cl.ucn.alertacuida.configuracion;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Vigilante del escalamiento (alertacuida.escalamiento.* en application.properties).
 *
 * @param plazo     tiempo sin que el reloj pida avisos tras el que se avisa a los coordinadores
 * @param intervalo cada cuánto corre la tarea programada; lo lee directamente TareasProgramadas
 */
@Validated
@ConfigurationProperties("alertacuida.escalamiento")
public record PropiedadesEscalamiento(@NotNull Duration plazo, @NotNull Duration intervalo) {
}
