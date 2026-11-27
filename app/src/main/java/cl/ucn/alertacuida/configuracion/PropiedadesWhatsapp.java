package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.aviso.modelo.PlantillaWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaConversacionWhatsapp;
import jakarta.validation.constraints.Min;
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
 * @param quienSinAlias valor de la variable "alias_paciente" si el paciente no tiene alias
 * @param plantillas    las plantillas aprobadas en Meta (con respaldo opcional sin mapa)
 * @param conversacion  plazos de llamada, recordatorios y tareas del flujo conversacional
 */
@Validated
@ConfigurationProperties("alertacuida.whatsapp")
public record PropiedadesWhatsapp(@NotNull Canal canal, @NotNull Duration intervalo,
		@NotEmpty List<Duration> reintentos, URI simuladorUrl, @NotBlank String versionApi, String token,
		String phoneNumberId, String appSecret, String verifyToken, @NotBlank String quienSinAlias,
		@NotNull Plantillas plantillas, @NotNull Conversacion conversacion) {

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
	 * Plantillas aprobadas en Meta (decisiones del 26-11-2026) y sus respaldos opcionales sin mapa.
	 *
	 * @param nivel1Y2                    aviso inicial para Nivel 1 (PRLAC) y Nivel 2 (Tercero significativo)
	 * @param nivel1Y2SinMapa             respaldo sin mapa si la alerta no tiene GPS (opcional)
	 * @param nivel3Edad                  aviso inicial para Nivel 3 con edad (y Carabineros por defecto)
	 * @param nivel3EdadSinMapa           respaldo sin mapa de Nivel 3 edad (opcional)
	 * @param nivel3Rut                   aviso inicial para Carabineros con RUT (opcional)
	 * @param nivel3RutSinMapa            respaldo sin mapa de Nivel 3 RUT (opcional)
	 * @param simulacro                   aviso inicial de simulacro
	 * @param simulacroSinMapa            respaldo sin mapa de simulacro (opcional)
	 * @param usarPlantillaRutCarabineros si Carabineros usa la plantilla con RUT en lugar de la de edad
	 */
	public record Plantillas(@NotNull PlantillaWhatsapp nivel1Y2, PlantillaWhatsapp nivel1Y2SinMapa,
			@NotNull PlantillaWhatsapp nivel3Edad, PlantillaWhatsapp nivel3EdadSinMapa,
			PlantillaWhatsapp nivel3Rut, PlantillaWhatsapp nivel3RutSinMapa,
			@NotNull PlantillaWhatsapp simulacro, PlantillaWhatsapp simulacroSinMapa,
			boolean usarPlantillaRutCarabineros) {
	}

	/**
	 * Plazos y cadencias de la máquina de estados conversacional de WhatsApp.
	 *
	 * @param plazoLlamada          límite para responder tras "Llamaré al paciente" (10 min por defecto)
	 * @param intervaloLeve         cadencia de recordatorios de llegada en LEVE (30 min)
	 * @param maximoLeve            tope de intervalos en LEVE (5 → 2 h 30 min)
	 * @param intervaloMedia        cadencia de recordatorios de llegada en MEDIA (15 min)
	 * @param maximoMedia           tope de intervalos en MEDIA (5 → 1 h 15 min)
	 * @param intervaloGrave        cadencia de recordatorios de llegada en GRAVE (5 min)
	 * @param maximoGrave           tope de intervalos en GRAVE (8 → 40 min)
	 * @param intervaloEnvioSaliente cadencia de la tarea de respaldo de mensajes de sesión
	 * @param intervaloVigilancia   cadencia de revisión de plazos vencidos
	 * @param envioInmediato        si el webhook dispara de inmediato el envío asíncrono de respuestas
	 */
	public record Conversacion(@NotNull Duration plazoLlamada, @NotNull Duration intervaloLeve, @Min(1) int maximoLeve,
			@NotNull Duration intervaloMedia, @Min(1) int maximoMedia, @NotNull Duration intervaloGrave,
			@Min(1) int maximoGrave, @NotNull Duration intervaloEnvioSaliente, @NotNull Duration intervaloVigilancia,
			boolean envioInmediato) {

		/** @return política lista para los casos de uso de conversación */
		public PoliticaConversacionWhatsapp politica() {
			return new PoliticaConversacionWhatsapp(plazoLlamada, intervaloLeve, maximoLeve, intervaloMedia,
					maximoMedia, intervaloGrave, maximoGrave);
		}
	}

	/** Sin las credenciales: que no aparezcan en los logs. */
	@Override
	public String toString() {
		return "PropiedadesWhatsapp[canal=" + canal + ", versionApi=" + versionApi + ", plantillas=" + plantillas + "]";
	}

}
