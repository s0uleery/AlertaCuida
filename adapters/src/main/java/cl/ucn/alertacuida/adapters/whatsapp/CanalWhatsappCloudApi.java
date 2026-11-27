package cl.ucn.alertacuida.adapters.whatsapp;

import cl.ucn.alertacuida.application.aviso.PlantillasWhatsapp;
import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.error.EnvioRechazado;
import cl.ucn.alertacuida.application.aviso.modelo.BotonInteractivo;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeSesionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.ParametroPlantilla;
import cl.ucn.alertacuida.application.aviso.modelo.UbicacionWhatsapp;
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
 * Envía WhatsApp con la Cloud API de Meta: {@code POST {base}/{version}/{phone-number-id}/messages}, con el token
 * como {@code Authorization: Bearer}. Soporta:
 * <ul>
 * <li>Plantillas aprobadas ({@code type: "template"}), con parámetros {@code NAMED} o {@code POSITIONAL},
 * encabezado {@code HEADER: LOCATION} y botones {@code quick_reply}.</li>
 * <li>Mensajes de sesión dentro de la ventana de 24 h ({@code type: "text"} e {@code interactive} de botones).</li>
 * </ul>
 * La misma clase sirve para el simulador de contactos: solo cambia la dirección base.
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

	/** @return el wamid del mensaje, para seguir su entrega y correlacionar respuestas por el webhook */
	@Override
	public String enviar(WhatsappPendiente aviso) {
		MensajeWhatsapp mensaje = plantillas.armar(aviso);
		return publicar(cuerpo(mensaje));
	}

	/** @return el wamid del mensaje de sesión (texto o botones interactivos) */
	@Override
	public String enviarSesion(MensajeSesionWhatsapp mensaje) {
		return publicar(cuerpoSesion(mensaje));
	}

	/** Publica un cuerpo JSON en /{version}/{phone-number-id}/messages y devuelve su wamid. */
	private String publicar(Map<String, Object> cuerpoJson) {
		HttpRequest pedido = HttpRequest.newBuilder(mensajes)
				.timeout(PLAZO)
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpoJson)))
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

	/** @return el cuerpo del envío de una plantilla, como lo pide la Cloud API (NAMED/POSITIONAL + LOCATION) */
	static Map<String, Object> cuerpo(MensajeWhatsapp mensaje) {
		List<Map<String, Object>> componentes = new ArrayList<>();
		if (mensaje.ubicacion() != null) {
			componentes.add(componenteUbicacion(mensaje.ubicacion()));
		}
		if (!mensaje.parametros().isEmpty()) {
			componentes.add(Map.of("type", "body", "parameters",
					mensaje.parametros().stream().map(CanalWhatsappCloudApi::parametroTexto).toList()));
		}
		for (int i = 0; i < mensaje.botones().size(); i++) {
			componentes.add(Map.of("type", "button", "sub_type", "quick_reply", "index", String.valueOf(i),
					"parameters", List.of(Map.of("type", "payload", "payload", mensaje.botones().get(i)))));
		}
		Map<String, Object> plantilla = new LinkedHashMap<>();
		plantilla.put("name", mensaje.plantilla());
		plantilla.put("language", Map.of("code", mensaje.idioma()));
		if (!componentes.isEmpty()) {
			plantilla.put("components", componentes);
		}
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("messaging_product", "whatsapp");
		// Meta pide el número con código de país y sin "+"
		cuerpo.put("to", soloDigitos(mensaje.telefono()));
		cuerpo.put("type", "template");
		cuerpo.put("template", plantilla);
		return cuerpo;
	}

	/** @return el cuerpo de un mensaje de sesión: texto libre o interactivo con botones de respuesta rápida */
	static Map<String, Object> cuerpoSesion(MensajeSesionWhatsapp mensaje) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("messaging_product", "whatsapp");
		cuerpo.put("recipient_type", "individual");
		cuerpo.put("to", soloDigitos(mensaje.telefono()));
		if (mensaje.botones().isEmpty()) {
			cuerpo.put("type", "text");
			cuerpo.put("text", Map.of("body", mensaje.cuerpo()));
			return cuerpo;
		}
		List<Map<String, Object>> botones = mensaje.botones().stream()
				.map(CanalWhatsappCloudApi::botonInteractivo)
				.toList();
		Map<String, Object> interactivo = new LinkedHashMap<>();
		interactivo.put("type", "button");
		interactivo.put("body", Map.of("text", mensaje.cuerpo()));
		interactivo.put("action", Map.of("buttons", botones));
		cuerpo.put("type", "interactive");
		cuerpo.put("interactive", interactivo);
		return cuerpo;
	}

	/** @return el componente HEADER de tipo LOCATION exigido por Meta */
	private static Map<String, Object> componenteUbicacion(UbicacionWhatsapp ubicacion) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("latitude", ubicacion.latitud());
		datos.put("longitude", ubicacion.longitud());
		datos.put("name", ubicacion.nombre());
		datos.put("address", ubicacion.direccion());
		return Map.of("type", "header", "parameters", List.of(Map.of("type", "location", "location", datos)));
	}

	/** @return un parámetro de cuerpo en formato NAMED (con parameter_name) o POSITIONAL */
	private static Map<String, Object> parametroTexto(ParametroPlantilla parametro) {
		Map<String, Object> mapa = new LinkedHashMap<>();
		mapa.put("type", "text");
		if (parametro.nombre() != null) {
			mapa.put("parameter_name", parametro.nombre());
		}
		mapa.put("text", parametro.valor());
		return mapa;
	}

	/** @return la representación de un botón interactivo en la Cloud API */
	private static Map<String, Object> botonInteractivo(BotonInteractivo boton) {
		return Map.of("type", "reply", "reply", Map.of("id", boton.id(), "title", boton.titulo()));
	}

	/** @return solo los dígitos del número, sin "+" ni espacios */
	private static String soloDigitos(String telefono) {
		return telefono.replaceAll("\\D", "");
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
