package cl.ucn.alertacuida.configuracion;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * WhatsApp de los avisos (alertacuida.whatsapp.* en application.properties).
 *
 * @param canal      por dónde salen; hoy solo NO_CONFIGURADO
 * @param intervalo  cada cuánto corre la tarea programada; lo lee directamente TareasProgramadas
 * @param reintentos espera antes de cada reintento de un WhatsApp fallido
 */
@Validated
@ConfigurationProperties("alertacuida.whatsapp")
public record PropiedadesWhatsapp(@NotNull Canal canal, @NotNull Duration intervalo,
		@NotEmpty List<Duration> reintentos) {

	/** Por dónde salen los WhatsApp. */
	public enum Canal {
		/** Sin credenciales de Meta: no envía nada y cada aviso cae al correo de respaldo. */
		NO_CONFIGURADO
	}

}
