package cl.ucn.alertacuida.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Webhook de WhatsApp de punta a punta, según la guía "create webhook endpoint" de Meta y las decisiones del
 * 24-11-2026. Los cuerpos imitan los de Meta y se firman como Meta. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Verificación (GET): con hub.mode=subscribe y el Verify token correcto responde 200 con hub.challenge en
 * texto plano; si no, 403.</li>
 * <li>R2 Un POST sin firma o con una firma que no corresponde responde 401 y no cambia nada.</li>
 * <li>R3 Los estados "delivered" y "read" llenan sus fechas, aunque vengan varios en un POST; un reenvío idéntico
 * no cambia nada.</li>
 * <li>R4 "failed" deja el WhatsApp fallido con el código de Meta (y eso dispara el correo de respaldo).</li>
 * <li>R5 El botón "Voy en camino" confirma la alerta y deja el aviso al reloj, igual que el enlace del correo;
 * repetido, no se registra dos veces.</li>
 * <li>R6 Lo que no es nuestro (un wamid desconocido) responde 200 y no cambia nada.</li>
 * </ul>
 */
@SpringBootTest(properties = {"alertacuida.whatsapp.app-secret=" + RequisitosWebhookWhatsappTest.SECRETO,
	"alertacuida.whatsapp.verify-token=" + RequisitosWebhookWhatsappTest.VERIFICACION})
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosWebhookWhatsappTest {

	static final String SECRETO = "app-secret-de-prueba";
	static final String VERIFICACION = "token-de-verificacion";
	private static final String WEBHOOK = "/api/webhooks/whatsapp";
	private static final String WAMID = "wamid.HBgLNTY5MTExMTExMTE";
	private static final Instant ENVIO = Instant.parse("2026-11-24T15:00:00Z");

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;

	private UUID alerta;
	private UUID intento;

	/** Base vacía; Rosa con su reloj, una caída grave y un WhatsApp ya enviado a su hija, con su wamid. */
	@BeforeEach
	void preparar() {
		BaseDePrueba.vaciar(jdbc);
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-webhook', 'Rosa', CURRENT_DATE - 10, CURRENT_DATE - 10) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT)).query(UUID.class).single();
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-WEBHOOK', 'OPERATIVO')
				RETURNING id_dispositivo""").query(UUID.class).single();
		UUID asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, 'hash-token-webhook') RETURNING id_asignacion""")
				.param("r", reloj).param("p", paciente).param("inicio", Timestamp.from(ENVIO.minusSeconds(86_400)))
				.query(UUID.class).single();
		alerta = jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', 'CAIDA', 'GRAVE', :t, :t) RETURNING id_alerta""")
				.param("a", asignacion).param("t", Timestamp.from(ENVIO.minusSeconds(60))).query(UUID.class).single();
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, whatsapp, whatsapp_hash, fecha_opt_in)
				VALUES (:nombre, :whatsapp, 'hash-w-hija', now()) RETURNING id_contacto""")
				.param("nombre", cifrado.cifrar("Hija", ColumnaCifrada.CONTACTO_NOMBRE))
				.param("whatsapp", cifrado.cifrar("+56911111111", ColumnaCifrada.CONTACTO_WHATSAPP))
				.query(UUID.class).single();
		UUID vinculo = jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad)
				VALUES (:p, :c, 'FAMILIA', 1) RETURNING id_contacto_paciente""")
				.param("p", paciente).param("c", persona).query(UUID.class).single();
		intento = jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda, id_externo,
				fecha_hora_envio) VALUES (gen_random_uuid(), :a, :v, 'WHATSAPP', 1, :w, :t) RETURNING id_intento""")
				.param("a", alerta).param("v", vinculo).param("w", WAMID).param("t", Timestamp.from(ENVIO))
				.query(UUID.class).single();
	}

	// ---------- R1 ----------

	/** R1: la verificación de Meta devuelve el desafío solo con el token correcto. */
	@Test
	void laVerificacionDevuelveElDesafio() throws Exception {
		mvc.perform(get(WEBHOOK).param("hub.mode", "subscribe").param("hub.verify_token", VERIFICACION)
				.param("hub.challenge", "1158201444")).andExpect(status().isOk())
				.andExpect(content().string("1158201444"));
		mvc.perform(get(WEBHOOK).param("hub.mode", "subscribe").param("hub.verify_token", "otro")
				.param("hub.challenge", "1158201444")).andExpect(status().isForbidden());
		mvc.perform(get(WEBHOOK).param("hub.verify_token", VERIFICACION).param("hub.challenge", "1"))
				.andExpect(status().isForbidden());
	}

	// ---------- R2 ----------

	/** R2: sin firma o con una firma de otro cuerpo, 401 y nada cambia. */
	@Test
	void sinFirmaValidaNoCambiaNada() throws Exception {
		String cuerpo = estados(estado(WAMID, "delivered", 100));

		mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isUnauthorized());
		mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.header("X-Hub-Signature-256", firma(cuerpo + " "))).andExpect(status().isUnauthorized());

		assertThat(fecha("fecha_hora_entrega")).isNull();
	}

	// ---------- R3 ----------

	/** R3: entregado y leído en un mismo POST llenan sus fechas; repetirlo no cambia nada. */
	@Test
	void losEstadosLlenanSusFechasUnaSolaVez() throws Exception {
		String cuerpo = estados(estado(WAMID, "delivered", 100), estado(WAMID, "read", 200));

		enviar(cuerpo).andExpect(status().isOk());
		enviar(estados(estado(WAMID, "read", 900))).andExpect(status().isOk());

		assertThat(fecha("fecha_hora_entrega")).isEqualTo(ENVIO.plusSeconds(100));
		assertThat(fecha("fecha_hora_lectura")).isEqualTo(ENVIO.plusSeconds(200));
	}

	// ---------- R4 ----------

	/** R4: "failed" deja el aviso fallido con el código de Meta. */
	@Test
	void unFalloQuedaConElCodigoDeMeta() throws Exception {
		enviar(estados("""
				{"id": "%s", "status": "failed", "timestamp": "%d", "recipient_id": "56911111111",
				 "errors": [{"code": 131026, "title": "Message undeliverable"}]}"""
				.formatted(WAMID, ENVIO.plusSeconds(30).getEpochSecond()))).andExpect(status().isOk());

		assertThat(fecha("fecha_hora_fallo")).isEqualTo(ENVIO.plusSeconds(30));
		assertThat(jdbc.sql("SELECT motivo_fallo FROM registro_intento WHERE id_intento = :i").param("i", intento)
				.query(String.class).single()).contains("131026");
	}

	// ---------- R5 ----------

	/** R5: "Voy en camino" confirma la alerta y deja el aviso al reloj, una sola vez. */
	@Test
	void elBotonVoyConfirmaLaAlerta() throws Exception {
		String boton = mensajes("""
				{"from": "56911111111", "id": "wamid.RESPUESTA", "timestamp": "%d", "type": "button",
				 "context": {"from": "56900000000", "id": "%s"},
				 "button": {"payload": "VOY_EN_CAMINO", "text": "Voy en camino"}}"""
				.formatted(ENVIO.plusSeconds(60).getEpochSecond(), WAMID));

		enviar(boton).andExpect(status().isOk());
		enviar(boton).andExpect(status().isOk());

		assertThat(jdbc.sql("SELECT respuesta FROM registro_intento WHERE id_intento = :i").param("i", intento)
				.query(String.class).single()).isEqualTo("VOY_EN_CAMINO");
		assertThat(jdbc.sql("SELECT estado_contacto FROM alerta_estado WHERE id_alerta = :a").param("a", alerta)
				.query(String.class).single()).isEqualTo("CONFIRMADA");
		assertThat(jdbc.sql("SELECT count(*) FROM aviso_fcm WHERE id_alerta = :a AND tipo = 'ALERTA_CONFIRMADA'")
				.param("a", alerta).query(Integer.class).single()).isEqualTo(1);
	}

	// ---------- R6 ----------

	/** R6: un estado o un botón de un mensaje que no es nuestro responde 200 y no cambia nada. */
	@Test
	void loAjenoSeIgnora() throws Exception {
		enviar(estados(estado("wamid.DE_OTRO", "delivered", 100))).andExpect(status().isOk());
		enviar(mensajes("""
				{"from": "56911111111", "id": "wamid.X", "timestamp": "1", "type": "text",
				 "text": {"body": "hola"}}"""))
				.andExpect(status().isOk());

		assertThat(fecha("fecha_hora_entrega")).isNull();
		assertThat(jdbc.sql("SELECT respuesta FROM registro_intento WHERE id_intento = :i").param("i", intento)
				.query((fila, n) -> fila.getString(1)).list().getFirst()).isNull();
	}

	// ---------- Ayudas ----------

	/** Envía el cuerpo firmado como Meta. */
	private ResultActions enviar(String cuerpo) throws Exception {
		return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.header("X-Hub-Signature-256", firma(cuerpo)));
	}

	/** @return un estado de Meta, a tantos segundos del envío */
	private static String estado(String id, String estado, int segundos) {
		return """
				{"id": "%s", "status": "%s", "timestamp": "%d", "recipient_id": "56911111111"}"""
				.formatted(id, estado, ENVIO.plusSeconds(segundos).getEpochSecond());
	}

	/** @return un webhook de Meta con esos estados, cada uno en su propia entrada (como llega en lote) */
	private static String estados(String... estados) {
		StringBuilder entradas = new StringBuilder();
		for (String estado : estados) {
			entradas.append(entradas.isEmpty() ? "" : ",").append(entrada("\"statuses\": [" + estado + "]"));
		}
		return "{\"object\": \"whatsapp_business_account\", \"entry\": [" + entradas + "]}";
	}

	/** @return un webhook de Meta con ese mensaje entrante */
	private static String mensajes(String mensaje) {
		return "{\"object\": \"whatsapp_business_account\", \"entry\": [" + entrada(
				"\"contacts\": [{\"profile\": {\"name\": \"Hija\"}, \"wa_id\": \"56911111111\"}], \"messages\": ["
						+ mensaje + "]") + "]}";
	}

	/** @return una entrada con el contenido de su value */
	private static String entrada(String contenido) {
		return """
				{"id": "102290129340398", "changes": [{"field": "messages", "value": {
				 "messaging_product": "whatsapp",
				 "metadata": {"display_phone_number": "56900000000", "phone_number_id": "106540352242922"},
				 %s}}]}""".formatted(contenido);
	}

	/** @return la cabecera X-Hub-Signature-256 de ese cuerpo, como la calcula Meta */
	private static String firma(String cuerpo) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
	}

	/** @return esa fecha del WhatsApp, o null */
	private Instant fecha(String columna) {
		Timestamp valor = jdbc.sql("SELECT " + columna + " FROM registro_intento WHERE id_intento = :i")
				.param("i", intento).query((fila, n) -> fila.getTimestamp(1)).list().getFirst();
		return valor == null ? null : valor.toInstant();
	}

}
