package cl.ucn.alertacuida.configuracion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Emparejamiento del reloj con código (alertacuida.emparejamiento.* en application.properties).
 *
 * @param vigencia         cuánto dura un código antes de vencer
 * @param maximoPendientes sobre cuántos códigos pendientes a la vez se responde 429 (protege la ruta pública)
 * @param segundosConsulta cada cuántos segundos el reloj pregunta si ya lo vincularon
 */
@Validated
@ConfigurationProperties("alertacuida.emparejamiento")
public record PropiedadesEmparejamiento(@NotNull Duration vigencia, @Min(1) int maximoPendientes,
		@Min(1) int segundosConsulta) {
}
