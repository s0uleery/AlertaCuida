package cl.ucn.alertacuida.adapters.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.aviso.PlantillasWhatsapp;
import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.error.EnvioRechazado;
import cl.ucn.alertacuida.application.aviso.modelo.BotonInteractivo;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeSesionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.PlantillaWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.application.aviso.modelo.UbicacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envío por la Cloud API de Meta contra un servidor HTTP falso: plantillas con {@code parameter_name} y
 * {@code HEADER: LOCATION}, mensajes de sesión (texto e interactivos con botones), wamid devuelto y manejo de
 * errores (5xx/429 se reintentan; 4xx es rechazo definitivo).
 */
class CanalWhatsappCloudApiTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final PlantillaWhatsapp PLANTILLA = new PlantillaWhatsapp("aviso_inicial_nivel_1_y_2", "es_CL",
			true, true, List.of("alias_paciente", "id_alerta"), List.of("TOMAR_LLAMADA", "NO_DISPONIBLE"));

	private HttpServer meta;
	private final AtomicReference<String> ruta = new AtomicReference<>();
	private final AtomicReference<String> autorizacion = new AtomicReference<>();
	private final AtomicReference<String> cuerpo = new AtomicReference<>();
	private int estado = 200;
	private String respuesta = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.ABC\"}]}";

	/** Un servidor local que guarda lo que recibe y responde lo configurado. */
	@BeforeEach
	void levantar() throws IOException {
		meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		meta.createContext("/", intercambio -> {
			ruta.set(intercambio.getRequestURI().getPath());
			autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
			cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
			intercambio.sendResponseHeaders(estado, bytes.length);
			intercambio.getResponseBody().write(bytes);
			intercambio.close();
		});
		meta.start();
	}

	/** Lo detiene. */
	@AfterEach
	void detener() {
		meta.stop(0);
	}

	/** El envío va a /{version}/{phone-number-id}/messages, con HEADER LOCATION, parameter_name y botones. */
	@Test
	void enviaLaPlantillaConUbicacionParametrosNombradosYBotones() {
		String wamid = canal().enviar(aviso());

		assertThat(wamid).isEqualTo("wamid.ABC");
		assertThat(ruta.get()).isEqualTo("/v23.0/123456/messages");
		assertThat(autorizacion.get()).isEqualTo("Bearer token-secreto");
		JsonNode enviado = JSON.readTree(cuerpo.get());
		assertThat(enviado.path("messaging_product").asString()).isEqualTo("whatsapp");
		assertThat(enviado.path("to").asString()).isEqualTo("56911111111");
		assertThat(enviado.path("type").asString()).isEqualTo("template");
		JsonNode plantilla = enviado.path("template");
		assertThat(plantilla.path("name").asString()).isEqualTo("aviso_inicial_nivel_1_y_2");
		assertThat(plantilla.path("language").path("code").asString()).isEqualTo("es_CL");
		JsonNode header = plantilla.path("components").path(0);
		assertThat(header.path("type").asString()).isEqualTo("header");
		assertThat(header.path("parameters").path(0).path("type").asString()).isEqualTo("location");
		assertThat(header.path("parameters").path(0).path("location").path("latitude").asDouble()).isEqualTo(-29.9533);
		JsonNode cuerpoPlantilla = plantilla.path("components").path(1);
		assertThat(cuerpoPlantilla.path("type").asString()).isEqualTo("body");
		assertThat(cuerpoPlantilla.path("parameters").path(0).path("parameter_name").asString())
				.isEqualTo("alias_paciente");
		assertThat(cuerpoPlantilla.path("parameters").path(0).path("text").asString()).isEqualTo("Rosa");
		assertThat(cuerpoPlantilla.path("parameters").path(1).path("parameter_name").asString())
				.isEqualTo("id_alerta");
		assertThat(cuerpoPlantilla.path("parameters").path(1).path("text").asString()).isEqualTo("#142");
		JsonNode segundoBoton = plantilla.path("components").path(3);
		assertThat(segundoBoton.path("sub_type").asString()).isEqualTo("quick_reply");
		assertThat(segundoBoton.path("index").asString()).isEqualTo("1");
		assertThat(segundoBoton.path("parameters").path(0).path("payload").asString()).isEqualTo("NO_DISPONIBLE");
	}

	/** Los mensajes de sesión soportan texto simple y botones interactivos de respuesta rápida. */
	@Test
	void enviaMensajesDeSesionDeTextoEInteractivos() {
		String wamidTexto = canal().enviarSesion(new MensajeSesionWhatsapp("+56911111111", "Alerta cerrada",
				List.of()));
		assertThat(wamidTexto).isEqualTo("wamid.ABC");
		JsonNode texto = JSON.readTree(cuerpo.get());
		assertThat(texto.path("type").asString()).isEqualTo("text");
		assertThat(texto.path("text").path("body").asString()).isEqualTo("Alerta cerrada");

		canal().enviarSesion(new MensajeSesionWhatsapp("+56911111111", "¿Cómo está la persona?",
				List.of(new BotonInteractivo("LLAMADA_BIEN", "Está bien"),
						new BotonInteractivo("LLAMADA_NO_CONTESTA", "No contesta"))));
		JsonNode interactivo = JSON.readTree(cuerpo.get());
		assertThat(interactivo.path("type").asString()).isEqualTo("interactive");
		assertThat(interactivo.path("interactive").path("type").asString()).isEqualTo("button");
		assertThat(interactivo.path("interactive").path("body").path("text").asString())
				.isEqualTo("¿Cómo está la persona?");
		assertThat(interactivo.path("interactive").path("action").path("buttons").path(0).path("reply").path("id")
				.asString()).isEqualTo("LLAMADA_BIEN");
	}

	/** Meta caído o con demasiadas peticiones: se reintenta. */
	@Test
	void unErrorDelServidorOLimiteSeReintenta() {
		estado = 503;
		assertThatThrownBy(() -> canal().enviar(aviso())).isInstanceOf(EnvioFallido.class);
		estado = 429;
		assertThatThrownBy(() -> canal().enviar(aviso())).isInstanceOf(EnvioFallido.class);
	}

	/** Un 4xx es un rechazo definitivo, con el código y el mensaje de Meta. */
	@Test
	void unRechazoDeMetaNoSeReintenta() {
		estado = 400;
		respuesta = "{\"error\":{\"message\":\"Template name does not exist\",\"code\":132001}}";

		assertThatThrownBy(() -> canal().enviar(aviso())).isInstanceOf(EnvioRechazado.class)
				.hasMessageContaining("132001").hasMessageContaining("Template name does not exist");
	}

	/** Sin conexión: se reintenta. */
	@Test
	void sinConexionSeReintenta() throws IOException {
		int libre;
		try (java.net.ServerSocket puerto = new java.net.ServerSocket(0)) {
			libre = puerto.getLocalPort();
		}
		PlantillasWhatsapp plantillas = new PlantillasWhatsapp(PLANTILLA, null, PLANTILLA, null, null, null,
				PLANTILLA, null, false, "alguien");
		CanalWhatsappCloudApi sinMeta = new CanalWhatsappCloudApi(URI.create("http://127.0.0.1:" + libre),
				"v23.0", "123456", "token-secreto", plantillas, JSON);

		assertThatThrownBy(() -> sinMeta.enviar(aviso())).isInstanceOf(EnvioFallido.class);
	}

	/** @return el canal apuntando al servidor falso */
	private CanalWhatsappCloudApi canal() {
		PlantillasWhatsapp plantillas = new PlantillasWhatsapp(PLANTILLA, null, PLANTILLA, null, null, null,
				PLANTILLA, null, false, "la persona que usted apoya");
		return new CanalWhatsappCloudApi(URI.create("http://127.0.0.1:" + meta.getAddress().getPort() + "/"),
				"v23.0", "123456", "token-secreto", plantillas, JSON);
	}

	/** @return un aviso leve de Rosa con GPS, alerta #142 */
	private static WhatsappPendiente aviso() {
		return new WhatsappPendiente(UUID.randomUUID(), UUID.randomUUID(), "+56911111111", 142, "Rosa",
				"Rosa Morales", 84, "12.345.678-5", "Av. Costanera 123", "+56987654321", TipoEvento.CAIDA,
				Severidad.LEVE, Instant.parse("2026-11-26T18:12:00Z"),
				new UbicacionWhatsapp(-29.9533, -71.3436, "Ubicación del paciente", "Av. Costanera 123"),
				false, 0, false, "TERCERO_SIGNIFICATIVO", Proposito.AVISO);
	}

}
