package cl.ucn.alertacuida.adapters.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.aviso.PlantillasWhatsapp;
import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.error.EnvioRechazado;
import cl.ucn.alertacuida.application.aviso.modelo.PlantillaWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envío por la Cloud API de Meta contra un servidor HTTP falso: la ruta, el token, el cuerpo de la plantilla con
 * sus valores y botones, el wamid devuelto, y cómo se tratan los errores (decisión del 24-11-2026: 5xx y 429 se
 * reintentan; un 4xx es un rechazo definitivo).
 */
class CanalWhatsappCloudApiTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final PlantillaWhatsapp LEVE = new PlantillaWhatsapp("alerta_leve", "es",
			List.of("quien", "folio"), List.of("VOY_EN_CAMINO", "NO_PUEDO"));

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

	/** El envío va a /{version}/{phone-number-id}/messages, con el token y la plantilla rellenada. */
	@Test
	void enviaLaPlantillaConSusValoresYBotones() {
		String wamid = canal().enviar(aviso());

		assertThat(wamid).isEqualTo("wamid.ABC");
		assertThat(ruta.get()).isEqualTo("/v23.0/123456/messages");
		assertThat(autorizacion.get()).isEqualTo("Bearer token-secreto");
		JsonNode enviado = JSON.readTree(cuerpo.get());
		assertThat(enviado.path("messaging_product").asString()).isEqualTo("whatsapp");
		assertThat(enviado.path("to").asString()).isEqualTo("56911111111");
		assertThat(enviado.path("type").asString()).isEqualTo("template");
		JsonNode plantilla = enviado.path("template");
		assertThat(plantilla.path("name").asString()).isEqualTo("alerta_leve");
		assertThat(plantilla.path("language").path("code").asString()).isEqualTo("es");
		JsonNode cuerpoPlantilla = plantilla.path("components").path(0);
		assertThat(cuerpoPlantilla.path("type").asString()).isEqualTo("body");
		assertThat(cuerpoPlantilla.path("parameters").path(0).path("text").asString()).isEqualTo("Rosa");
		assertThat(cuerpoPlantilla.path("parameters").path(1).path("text").asString()).isEqualTo("#142");
		JsonNode segundoBoton = plantilla.path("components").path(2);
		assertThat(segundoBoton.path("sub_type").asString()).isEqualTo("quick_reply");
		assertThat(segundoBoton.path("index").asString()).isEqualTo("1");
		assertThat(segundoBoton.path("parameters").path(0).path("payload").asString()).isEqualTo("NO_PUEDO");
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
		PlantillasWhatsapp plantillas = new PlantillasWhatsapp(LEVE, LEVE, LEVE, "alguien");
		CanalWhatsappCloudApi sinMeta = new CanalWhatsappCloudApi(URI.create("http://127.0.0.1:" + libre),
				"v23.0", "123456", "token-secreto", plantillas, JSON);

		assertThatThrownBy(() -> sinMeta.enviar(aviso())).isInstanceOf(EnvioFallido.class);
	}

	/** @return el canal apuntando al servidor falso */
	private CanalWhatsappCloudApi canal() {
		PlantillasWhatsapp plantillas = new PlantillasWhatsapp(LEVE, LEVE, LEVE, "la persona que usted apoya");
		return new CanalWhatsappCloudApi(URI.create("http://127.0.0.1:" + meta.getAddress().getPort() + "/"),
				"v23.0", "123456", "token-secreto", plantillas, JSON);
	}

	/** @return un aviso leve de Rosa, alerta #142 */
	private static WhatsappPendiente aviso() {
		return new WhatsappPendiente(UUID.randomUUID(), "+56911111111", 142, "Rosa", TipoEvento.CAIDA,
				Severidad.LEVE, false, 0);
	}

}
