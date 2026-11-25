package cl.ucn.alertacuida.adapters.whatsapp;

import cl.ucn.alertacuida.application.aviso.PlantillasWhatsapp;
import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.error.EnvioRechazado;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.CanalWhatsapp;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Envía los WhatsApp con la Cloud API de Meta: {@code POST {base}/{version}/{phone-number-id}/messages}, con el
 * token como {@code Authorization: Bearer}. Siempre manda una plantilla aprobada (los avisos los inicia el
 * servidor, así que Meta no admite texto libre), con sus valores y el payload de cada botón. La misma clase
 * sirve para el simulador de contactos, que imita esa ruta: solo cambia la dirección base.
 *
 * <p>Si Meta está caído, responde 429 o no hay conexión, lanza {@link EnvioFallido} y se reintenta. Si rechaza
 * el mensaje (4xx: número, plantilla o token), lanza {@link EnvioRechazado}: reintentar daría lo mismo.
 */
public final class CanalWhatsappCloudApi implements CanalWhatsapp {

	/** Plazo de cada llamada: la tarea programada no debe quedar esperando. */
	private static final Duration PLAZO = Duration.ofSeconds(10);

	private final URI mensajes;
	private final String token;
	private final PlantillasWhatsapp plantillas;
	private final ObjectMapper json;
	private final HttpClient cliente;

	/**
	 * @param base          https://graph.facebook.com o la dirección del simulador
	 * @param version       versión de la Graph API, ej. "v23.0"
	 * @param phoneNumberId el Phone number ID del número que envía (no es el número)
	 * @param token         token permanente del usuario del sistema; secreto
	 * @param plantillas    qué plantilla lleva cada aviso
	 * @param json          para armar y leer el JSON
	 */
	public CanalWhatsappCloudApi(URI base, String version, String phoneNumberId, String token,
			PlantillasWhatsapp plantillas, ObjectMapper json) {
		String raiz = base.toString().replaceAll("/+$", "");
		this.mensajes = URI.create(raiz + "/" + version + "/" + phoneNumberId + "/messages");
		this.token = token;
		this.plantillas = plantillas;
		this.json = json;
		this.cliente = HttpClient.newBuilder().connectTimeout(PLAZO).build();
	}

	/** @return el wamid del mensaje, para seguir su entrega por el webhook */
	@Override
	public String enviar(WhatsappPendiente aviso) {
		MensajeWhatsapp mensaje = plantillas.armar(aviso);
		HttpRequest pedido = HttpRequest.newBuilder(mensajes)
				.timeout(PLAZO)
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo(mensaje))))
				.build();
		HttpResponse<String> respuesta = mandar(pedido);
		int estado = respuesta.statusCode();
		if (estado >= 200 && estado < 300) {
			return idDelMensaje(respuesta.body());
		}
		String motivo = "Meta respondió " + estado + error(respuesta.body());
		if (estado == 429 || estado >= 500) {
			throw new EnvioFallido(motivo);
		}
		throw new EnvioRechazado(motivo);
	}

	/** @return el cuerpo del envío de una plantilla, como lo pide la Cloud API */
	static Map<String, Object> cuerpo(MensajeWhatsapp mensaje) {
		List<Map<String, Object>> componentes = new ArrayList<>();
		if (!mensaje.valores().isEmpty()) {
			componentes.add(Map.of("type", "body", "parameters", mensaje.valores().stream()
					.map(valor -> Map.of("type", "text", "text", valor)).toList()));
		}
		for (int i = 0; i < mensaje.botones().size(); i++) {
			componentes.add(Map.of("type", "button", "sub_type", "quick_reply", "index", String.valueOf(i),
					"parameters", List.of(Map.of("type", "payload", "payload", mensaje.botones().get(i)))));
		}
		Map<String, Object> plantilla = new LinkedHashMap<>();
		plantilla.put("name", mensaje.plantilla());
		plantilla.put("language", Map.of("code", mensaje.idioma()));
		plantilla.put("components", componentes);
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("messaging_product", "whatsapp");
		// Meta pide el número con código de país y sin "+"
		cuerpo.put("to", mensaje.telefono().replaceAll("\\D", ""));
		cuerpo.put("type", "template");
		cuerpo.put("template", plantilla);
		return cuerpo;
	}

	/** @return la respuesta de Meta, o EnvioFallido si no hubo conexión */
	private HttpResponse<String> mandar(HttpRequest pedido) {
		try {
			return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new EnvioFallido("No se pudo conectar con WhatsApp en " + mensajes.getHost(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new EnvioFallido("Se interrumpió el envío del WhatsApp", e);
		}
	}

	/** @return {@code messages[0].id}; EnvioFallido si Meta no lo devolvió */
	private String idDelMensaje(String cuerpo) {
		JsonNode id = json.readTree(cuerpo).path("messages").path(0).path("id");
		if (!id.isString() || id.asString().isBlank()) {
			throw new EnvioFallido("Meta aceptó el mensaje pero no devolvió su id");
		}
		return id.asString();
	}

	/** @return ": código y mensaje" del error de Meta, o vacío si no viene en el formato esperado */
	private String error(String cuerpo) {
		try {
			JsonNode error = json.readTree(cuerpo).path("error");
			if (error.isMissingNode()) {
				return "";
			}
			return " (código " + error.path("code").asString("?") + "): " + error.path("message").asString("");
		} catch (RuntimeException noEsJson) {
			return "";
		}
	}

}
