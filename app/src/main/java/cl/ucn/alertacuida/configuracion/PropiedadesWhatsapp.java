package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.aviso.modelo.PlantillaWhatsapp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * WhatsApp de los avisos (alertacuida.whatsapp.* en application.properties). Las credenciales llegan por
 * variables de entorno (WHATSAPP_*) y nunca se escriben en el repositorio.
 *
 * @param canal         por dónde salen
 * @param intervalo     cada cuánto corre la tarea programada; lo lee directamente TareasProgramadas
 * @param reintentos    espera antes de cada reintento de un WhatsApp fallido
 * @param simuladorUrl  dónde escucha el simulador de contactos (con canal SIMULADOR)
 * @param versionApi    versión de la Graph API de Meta, ej. "v23.0"
 * @param token         token permanente del usuario del sistema de Meta; secreto
 * @param phoneNumberId el Phone number ID del número que envía
 * @param appSecret     App Secret de la app de Meta, con que se firman los webhooks; secreto
 * @param verifyToken   el Verify token que se escribe en el panel de Meta; secreto
 * @param quienSinAlias valor de la variable "quien" si el paciente no tiene alias
 * @param plantillas    las plantillas aprobadas en Meta
 */
@Validated
@ConfigurationProperties("alertacuida.whatsapp")
public record PropiedadesWhatsapp(@NotNull Canal canal, @NotNull Duration intervalo,
		@NotEmpty List<Duration> reintentos, URI simuladorUrl, @NotBlank String versionApi, String token,
		String phoneNumberId, String appSecret, String verifyToken, @NotBlank String quienSinAlias,
		@NotNull Plantillas plantillas) {

	/** Por dónde salen los WhatsApp. */
	public enum Canal {
		/** Sin credenciales de Meta: no envía nada y cada aviso cae al correo de respaldo. */
		NO_CONFIGURADO,
		/** El simulador de contactos (simulador-whatsapp/), que imita la Cloud API: para desarrollo. */
		SIMULADOR,
		/** La Cloud API de Meta, de verdad. */
		META
	}

	/**
	 * Las tres plantillas aprobadas (decisión del 24-11-2026).
	 *
	 * @param leve      alertas leves y medias
	 * @param grave     alertas graves, con la instrucción de llamar al 131
	 * @param simulacro simulacros ("PRUEBA")
	 */
	public record Plantillas(@NotNull PlantillaWhatsapp leve, @NotNull PlantillaWhatsapp grave,
			@NotNull PlantillaWhatsapp simulacro) {
	}

	/** Sin las credenciales: que no aparezcan en los logs. */
	@Override
	public String toString() {
		return "PropiedadesWhatsapp[canal=" + canal + ", versionApi=" + versionApi + ", plantillas=" + plantillas + "]";
	}

}
