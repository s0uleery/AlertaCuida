package cl.ucn.alertacuida.configuracion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Correo institucional (alertacuida.correo.* en application.properties). La conexión al servidor SMTP
 * va aparte, en spring.mail.*.
 *
 * @param canal        SMTP envía de verdad; SIMULADO solo registra en el log (desarrollo)
 * @param remitente    la cuenta institucional desde la que salen los correos
 * @param urlDashboard dirección del dashboard que va en los correos al equipo
 * @param intervalo    cada cuánto corre la tarea programada; lo lee directamente TareasProgramadas
 * @param ventana      hasta qué tan atrás se buscan alertas y WhatsApp fallidos para encolar
 * @param reintentos   espera antes de cada reintento de un correo fallido
 */
@Validated
@ConfigurationProperties("alertacuida.correo")
public record PropiedadesCorreo(@NotNull Canal canal, @NotBlank @Email String remitente, @NotBlank String urlDashboard,
		@NotNull Duration intervalo, @NotNull Duration ventana, @NotEmpty List<Duration> reintentos) {

	/** Por dónde salen los correos. */
	public enum Canal {
		/** Servidor SMTP institucional (spring.mail.*). */
		SMTP,
		/** No envía nada: registra el correo en el log. */
		SIMULADO
	}

}
