package cl.ucn.alertacuida.adapters.web.whatsapp;

import cl.ucn.alertacuida.adapters.seguridad.firma.FirmaWebhookMeta;
import cl.ucn.alertacuida.application.aviso.RecibirWebhookWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.EventoWhatsapp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook de WhatsApp de Meta, según su guía "create webhook endpoint". Es público (ver ConfiguracionSeguridad):
 * lo autorizan el Verify token en la verificación y la firma HMAC-SHA256 en cada aviso.
 * <ul>
 * <li>GET: la verificación que hace Meta al guardar la Callback URL en el panel.</li>
 * <li>POST: estados de entrega y botones presionados (plantillas e interactivos). La firma se revisa sobre los
 * bytes tal como llegan; si es válida responde 200 (Meta reintenta todo lo que no sea 200).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WebhookWhatsappController {

	private final RecibirWebhookWhatsapp recibir;
	private final FirmaWebhookMeta firma;
	private final TokenVerificacionWebhook verificacion;
	private final ObjectMapper json;

	/**
	 * @param recibir      procesa los eventos
	 * @param firma        revisa X-Hub-Signature-256 con el App Secret
	 * @param verificacion el Verify token del panel de Meta
	 * @param json         para leer el cuerpo
	 */
	public WebhookWhatsappController(RecibirWebhookWhatsapp recibir, FirmaWebhookMeta firma,
			TokenVerificacionWebhook verificacion, ObjectMapper json) {
		this.recibir = recibir;
		this.firma = firma;
		this.verificacion = verificacion;
		this.json = json;
	}

	/**
	 * Verificación inicial de Meta.
	 *
	 * @return 200 con hub.challenge en texto plano si el modo es "subscribe" y el token coincide; 403 si no
	 */
	@GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> verificar(@RequestParam(name = "hub.mode", required = false) String modo,
			@RequestParam(name = "hub.verify_token", required = false) String token,
			@RequestParam(name = "hub.challenge", required = false) String desafio) {
		if ("subscribe".equals(modo) && desafio != null && tokenCorrecto(token)) {
			return ResponseEntity.ok(desafio);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	/**
	 * Un aviso de Meta: puede traer varios estados y mensajes juntos.
	 *
	 * @param cuerpo   los bytes tal como llegaron, para revisar la firma
	 * @param cabecera X-Hub-Signature-256
	 * @return 200 si la firma es válida (aunque nada sea nuestro); 401 si falta o no coincide; 400 si no es JSON
	 */
	@PostMapping(consumes = MediaType.ALL_VALUE)
	public ResponseEntity<Void> recibir(@RequestBody byte[] cuerpo,
			@RequestHeader(name = "X-Hub-Signature-256", required = false) String cabecera) {
		if (!firma.esValida(cuerpo, cabecera)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		JsonNode raiz;
		try {
			raiz = json.readTree(cuerpo);
		} catch (RuntimeException noEsJson) {
			return ResponseEntity.badRequest().build();
		}
		recibir.procesar(eventos(raiz));
		return ResponseEntity.ok().build();
	}

	/** @return los estados y botones de todas las entradas de campo "messages", en orden */
	static List<EventoWhatsapp> eventos(JsonNode raiz) {
		List<EventoWhatsapp> eventos = new ArrayList<>();
		for (JsonNode entrada : raiz.path("entry")) {
			for (JsonNode cambio : entrada.path("changes")) {
				String campo = cambio.path("field").asString("");
				if (!campo.isEmpty() && !"messages".equals(campo)) {
					continue;
				}
				JsonNode valor = cambio.path("value");
				for (JsonNode estado : valor.path("statuses")) {
					eventos.add(new EventoWhatsapp.Estado(estado.path("id").asString(""),
							estado.path("status").asString(""), fecha(estado.path("timestamp")), motivo(estado)));
				}
				for (JsonNode mensaje : valor.path("messages")) {
					String boton = valorDelBoton(mensaje);
					if (boton != null) {
						eventos.add(new EventoWhatsapp.Boton(
								textoOpcional(mensaje.path("id")),
								textoOpcional(mensaje.path("from")),
								textoOpcional(mensaje.path("context").path("id")),
								boton,
								fecha(mensaje.path("timestamp"))));
					}
				}
			}
		}
		return eventos;
	}

	/**
	 * @return el payload de un botón de plantilla (o su texto, si no trae payload) o el id de un botón
	 *         interactivo; null si el mensaje no es un botón
	 */
	private static String valorDelBoton(JsonNode mensaje) {
		String tipo = mensaje.path("type").asString("");
		if ("button".equals(tipo)) {
			JsonNode boton = mensaje.path("button");
			String payload = boton.path("payload").asString("");
			return payload.isBlank() ? textoOpcional(boton.path("text")) : payload;
		}
		if ("interactive".equals(tipo)) {
			JsonNode interactivo = mensaje.path("interactive");
			String id = textoOpcional(interactivo.path("button_reply").path("id"));
			return id != null ? id : textoOpcional(interactivo.path("list_reply").path("id"));
		}
		return null;
	}

	/** @return texto no vacío del nodo, o null */
	private static String textoOpcional(JsonNode nodo) {
		String texto = nodo.asString("");
		return texto.isBlank() ? null : texto;
	}

	/** @return "código título" del primer error de un "failed"; null si no trae */
	private static String motivo(JsonNode estado) {
		JsonNode error = estado.path("errors").path(0);
		if (error.isMissingNode()) {
			return null;
		}
		return (error.path("code").asString("") + " " + error.path("title").asString("")).strip();
	}

	/** @return la hora del evento (Meta manda segundos Unix como texto); la de ahora si no viene */
	private static Instant fecha(JsonNode segundos) {
		try {
			return Instant.ofEpochSecond(Long.parseLong(segundos.asString("")));
		} catch (NumberFormatException sinHora) {
			return Instant.now();
		}
	}

	/** Comparación en tiempo constante; sin token configurado, nada coincide. */
	private boolean tokenCorrecto(String token) {
		String esperado = verificacion.valor();
		if (esperado == null || esperado.isBlank() || token == null) {
			return false;
		}
		return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
				token.getBytes(StandardCharsets.UTF_8));
	}

}
