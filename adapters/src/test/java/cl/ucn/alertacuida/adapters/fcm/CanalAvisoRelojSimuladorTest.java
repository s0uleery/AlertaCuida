package cl.ucn.alertacuida.adapters.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.avisoreloj.error.TokenRelojRechazado;
import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.modelo.TipoAvisoReloj;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Canal que entrega los avisos al simulador de reloj por HTTP local, en vez de Firebase. Un servidor HTTP
 * de prueba hace de simulador. Requisitos (decisión del 11-11-2026, canal local para el simulador):
 * <ul>
 * <li>R1 El aviso llega por POST a la dirección configurada, con el token del reloj y los mismos datos que
 * irían por Firebase (esquema AvisoFcm).</li>
 * <li>R2 Si el simulador responde 404 o 410 (no conoce ese reloj), el token se trata como rechazado, igual
 * que UNREGISTERED en Firebase.</li>
 * <li>R3 Cualquier otro error, o el simulador apagado, es una falla que se puede reintentar.</li>
 * </ul>
 */
class CanalAvisoRelojSimuladorTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final UUID ID_AVISO = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID EVENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final UUID ID_CONTACTO = UUID.fromString("33333333-3333-4333-8333-333333333333");

	private HttpServer simulador;
	private final AtomicReference<String> cuerpo = new AtomicReference<>();
	private final AtomicReference<String> tipoContenido = new AtomicReference<>();
	private final AtomicInteger estado = new AtomicInteger(204);

	/** Levanta un simulador de prueba en un puerto libre que guarda lo que recibe. */
	@BeforeEach
	void levantarSimulador() throws IOException {
		simulador = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		simulador.createContext("/avisos", intercambio -> {
			cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			tipoContenido.set(intercambio.getRequestHeaders().getFirst("Content-Type"));
			intercambio.sendResponseHeaders(estado.get(), -1);
			intercambio.close();
		});
		simulador.start();
	}

	/** Apaga el simulador de prueba. */
	@AfterEach
	void apagarSimulador() {
		simulador.stop(0);
	}

	/** R1: llega el token y los datos del aviso, en JSON. */
	@Test
	void entregaElTokenYLosDatosDelAviso() {
		AvisoReloj aviso = new AvisoReloj("token-sim-1", TipoAvisoReloj.ALERTA_CONFIRMADA, ID_AVISO, EVENT_ID,
				ID_CONTACTO);

		canal().enviar(aviso);

		assertThat(tipoContenido.get()).startsWith("application/json");
		Map<String, Object> recibido = recibido();
		assertThat(recibido).containsEntry("token", "token-sim-1").containsEntry("datos", Map.of(
				"tipo", "ALERTA_CONFIRMADA",
				"idAviso", ID_AVISO.toString(),
				"eventIdAlerta", EVENT_ID.toString(),
				"idContacto", ID_CONTACTO.toString()));
	}

	/** R1: un aviso de configuración nueva solo lleva el tipo. */
	@Test
	void configuracionNuevaSoloLlevaElTipo() {
		canal().enviar(AvisoReloj.configuracionNueva("token-sim-1"));

		Map<String, Object> recibido = recibido();
		assertThat(recibido.get("datos")).isEqualTo(Map.of("tipo", "CONFIGURACION_NUEVA"));
	}

	/** R2: el simulador no conoce el reloj. */
	@ParameterizedTest
	@ValueSource(ints = {404, 410})
	void relojDesconocidoEsTokenRechazado(int codigo) {
		estado.set(codigo);

		assertThatThrownBy(() -> canal().enviar(AvisoReloj.configuracionNueva("token-sim-1")))
				.isInstanceOf(TokenRelojRechazado.class);
	}

	/** R3: un error del simulador se puede reintentar; no es un token rechazado. */
	@ParameterizedTest
	@ValueSource(ints = {400, 500, 503})
	void otroErrorEsFallaReintentable(int codigo) {
		estado.set(codigo);

		assertThatThrownBy(() -> canal().enviar(AvisoReloj.configuracionNueva("token-sim-1")))
				.isInstanceOf(RuntimeException.class)
				.isNotInstanceOf(TokenRelojRechazado.class);
	}

	/** R3: con el simulador apagado, el envío falla y se puede reintentar. */
	@Test
	void simuladorApagadoEsFallaReintentable() {
		CanalAvisoRelojSimulador canal = canal();
		simulador.stop(0);

		assertThatThrownBy(() -> canal.enviar(AvisoReloj.configuracionNueva("token-sim-1")))
				.isInstanceOf(RuntimeException.class)
				.isNotInstanceOf(TokenRelojRechazado.class);
	}

	/** @return el canal apuntando al simulador de prueba */
	private CanalAvisoRelojSimulador canal() {
		URI direccion = URI.create("http://127.0.0.1:" + simulador.getAddress().getPort() + "/avisos");
		return new CanalAvisoRelojSimulador(direccion, JSON);
	}

	/** @return el último JSON que recibió el simulador de prueba */
	private Map<String, Object> recibido() {
		return JSON.readValue(cuerpo.get(), new TypeReference<>() { });
	}

}
