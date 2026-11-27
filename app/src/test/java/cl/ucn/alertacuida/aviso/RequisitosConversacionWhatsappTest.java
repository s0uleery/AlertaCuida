package cl.ucn.alertacuida.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.VigilarConversacionesWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.NivelConversacion;
import cl.ucn.alertacuida.application.aviso.puerto.ConversacionesWhatsapp;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import cl.ucn.alertacuida.soporte.RelojAjustable;
import cl.ucn.alertacuida.soporte.RelojDePrueba;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integración de punta a punta del flujo conversacional de WhatsApp v3 contra PostgreSQL real:
 * <ul>
 * <li>Flujo Nivel 2 completo por webhook hasta cierre parcial (sin resolutor, pendiente de validación PRLAC) y
 * consulta del reloj viendo {@code RESUELTA}.</li>
 * <li>Flujo Simulacro (despacho directo sin llamada previa) hasta cierre parcial por emergencia.</li>
 * <li>Carrera entre dos contactos y re-escalamiento por vencimiento de plazo de 10 minutos, devolviendo la
 * alerta a {@code AVISANDO_CONTACTO} con {@code CAMBIO_ESTADO_ALERTA} en {@code aviso_fcm}.</li>
 * </ul>
 */
@SpringBootTest(properties = {"alertacuida.whatsapp.app-secret=" + RequisitosConversacionWhatsappTest.SECRETO,
	"alertacuida.whatsapp.verify-token=verificacion-conv"})
@AutoConfigureMockMvc
@Import({ PostgresDePrueba.class, RelojDePrueba.class })
@Testcontainers(disabledWithoutDocker = true)
class RequisitosConversacionWhatsappTest {

	static final String SECRETO = "secreto-conversacion-prueba";
	private static final String TOKEN_RELOJ = "token-reloj-conversacion";
	private static final String WEBHOOK = "/api/webhooks/whatsapp";
	private static final Instant INICIO = Instant.parse("2026-11-26T15:00:00Z");

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private SecretosUnSoloUso secretos;
	@Autowired
	private RelojAjustable reloj;
	@Autowired
	private ConversacionesWhatsapp conversaciones;
	@Autowired
	private VigilarConversacionesWhatsapp vigilar;

	private UUID paciente;
	private UUID asignacion;

	/** Base vacía; Rosa con su reloj operativo autenticado. */
	@BeforeEach
	void preparar() {
		reloj.fijar(INICIO);
		BaseDePrueba.vaciar(jdbc);
		paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, nombres, apellidos, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-conv', 'Rosa', :nom, :ape, CURRENT_DATE - 10, CURRENT_DATE - 10)
				RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.param("nom", cifrado.cifrar("Rosa", ColumnaCifrada.PACIENTE_NOMBRES))
				.param("ape", cifrado.cifrar("Morales", ColumnaCifrada.PACIENTE_APELLIDOS))
				.query(UUID.class).single();
		UUID relojId = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, numero_sim, estado)
				VALUES ('R-CONV', '+56987654321', 'OPERATIVO') RETURNING id_dispositivo""")
				.query(UUID.class).single();
		asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, :hash) RETURNING id_asignacion""")
				.param("r", relojId).param("p", paciente)
				.param("inicio", Timestamp.from(INICIO.minus(Duration.ofDays(1))))
				.param("hash", secretos.hash(TOKEN_RELOJ))
				.query(UUID.class).single();
	}

	/** Nivel 2 recorre todos los botones interactivos, deja cierre parcial y el reloj ve RESUELTA. */
	@Test
	void flujoNivel2CompletoDejaCierreParcialYRelojVeResuelta() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID alerta = alerta(eventId, "MEDIA", false);
		UUID intento = intentoWhatsapp(alerta, contacto("Hija", "+56911111111", "TERCERO_SIGNIFICATIVO", 1),
				"wamid.N2_INIT");
		conversaciones.crear(intento, alerta, NivelConversacion.NIVEL_2_TERCERO, "wamid.N2_INIT", INICIO);

		enviarBoton("wamid.E1", "wamid.N2_INIT", "TOMAR_LLAMADA", false);
		mvc.perform(get("/api/reloj/v1/alertas/" + eventId).header("Authorization", "Bearer " + TOKEN_RELOJ))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(true));

		enviarBoton("wamid.E2", "wamid.N2_INIT", "LLAMADA_NECESITA_ATENCION", true);
		enviarBoton("wamid.E3", "wamid.N2_INIT", "ATENDERE", true);
		enviarBoton("wamid.E4", "wamid.N2_INIT", "LLEGO", true);
		enviarBoton("wamid.E5", "wamid.N2_INIT", "RESULTADO_APOYO_NO_URGENTE", true);

		assertThat(jdbc.sql("""
				SELECT fecha_hora_cierre IS NOT NULL AND id_usuario_resolutor IS NULL
					AND clasificacion IS NULL AND resultado_cierre_whatsapp = 'APOYO_NO_URGENTE'
				FROM atencion_alerta WHERE id_alerta = :a""")
				.param("a", alerta).query(Boolean.class).single()).isTrue();
		assertThat(jdbc.sql("SELECT count(*) FROM aviso_fcm WHERE id_alerta = :a AND tipo = 'CAMBIO_ESTADO_ALERTA'")
				.param("a", alerta).query(Integer.class).single()).isEqualTo(2);
		assertThat(jdbc.sql("SELECT count(*) FROM mensaje_whatsapp_saliente").query(Integer.class).single())
				.isEqualTo(5);

		mvc.perform(get("/api/reloj/v1/alertas/" + eventId).header("Authorization", "Bearer " + TOKEN_RELOJ))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RESUELTA"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(true));
	}

	/** Simulacro activa el flujo completo directo sin llamada previa hasta cierre parcial por emergencia. */
	@Test
	void simulacroEjecutaFlujoCompletoSinLlamadaPrevia() throws Exception {
		UUID alerta = alerta(UUID.randomUUID(), "GRAVE", true);
		UUID intento = intentoWhatsapp(alerta, contacto("Carabineros", "+56933333333", "CARABINEROS", 1),
				"wamid.SIM_INIT");
		conversaciones.crear(intento, alerta, NivelConversacion.SIMULACRO, "wamid.SIM_INIT", INICIO);

		enviarBoton("wamid.S1", "wamid.SIM_INIT", "ATENDER_SIMULACRO", false);
		assertThat(etapa(intento)).isEqualTo("ESPERANDO_LLEGADA");

		enviarBoton("wamid.S2", "wamid.SIM_INIT", "LLEGO", true);
		enviarBoton("wamid.S3", "wamid.SIM_INIT", "RESULTADO_EMERGENCIA", true);

		assertThat(etapa(intento)).isEqualTo("COMPLETADA");
		assertThat(jdbc.sql("SELECT resultado_cierre_whatsapp FROM atencion_alerta WHERE id_alerta = :a")
				.param("a", alerta).query(String.class).single()).isEqualTo("EMERGENCIA");
	}

	/**
	 * Contacto 1 toma la alerta, Contacto 2 recibe aviso de que ya fue tomada, y si Contacto 1 vence el plazo de
	 * 10 min la alerta vuelve a {@code AVISANDO_CONTACTO} con un nuevo {@code CAMBIO_ESTADO_ALERTA} para el reloj.
	 */
	@Test
	void carreraEntreContactosYVencimientoDeLlamadaVuelvenAEscalar() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID alerta = alerta(eventId, "GRAVE", false);
		UUID i1 = intentoWhatsapp(alerta, contacto("Hija", "+56911111111", "TERCERO_SIGNIFICATIVO", 1), "wamid.C1");
		UUID i2 = intentoWhatsapp(alerta, contacto("Vecino", "+56922222222", "TERCERO_SIGNIFICATIVO", 2), "wamid.C2");
		conversaciones.crear(i1, alerta, NivelConversacion.NIVEL_2_TERCERO, "wamid.C1", INICIO);
		conversaciones.crear(i2, alerta, NivelConversacion.NIVEL_2_TERCERO, "wamid.C2", INICIO);

		enviarBoton("wamid.R1", "wamid.C1", "TOMAR_LLAMADA", false);
		enviarBoton("wamid.R2", "wamid.C2", "TOMAR_LLAMADA", false);

		assertThat(etapa(i1)).isEqualTo("ESPERANDO_ESTADO_LLAMADA");
		assertThat(etapa(i2)).isEqualTo("DESCARTADA");
		assertThat(jdbc.sql("""
				SELECT m.cuerpo FROM mensaje_whatsapp_saliente m
				JOIN conversacion_whatsapp c USING (id_conversacion)
				WHERE c.id_intento = :i""")
				.param("i", i2).query(String.class).single()).contains("ya fue tomada");

		// A los 11 min sin respuesta del contacto 1, el vigilante expira y notifica al reloj para escalar
		reloj.adelantar(Duration.ofMinutes(11));
		vigilar.vigilar();

		assertThat(etapa(i1)).isEqualTo("EXPIRADA");
		assertThat(jdbc.sql("SELECT count(*) FROM aviso_fcm WHERE id_alerta = :a AND tipo = 'CAMBIO_ESTADO_ALERTA'")
				.param("a", alerta).query(Integer.class).single()).isEqualTo(2);
		mvc.perform(get("/api/reloj/v1/alertas/" + eventId).header("Authorization", "Bearer " + TOKEN_RELOJ))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("AVISANDO_CONTACTO"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(false))
				.andExpect(jsonPath("$.confirmadaPor").doesNotExist());
	}

	/** Envía un webhook firmado de botón (template quick_reply o interactive button_reply). */
	private void enviarBoton(String idEntrante, String wamidOriginal, String payload, boolean interactivo)
			throws Exception {
		String mensaje = interactivo
				? """
						{"from": "56911111111", "id": "%s", "timestamp": "%d", "type": "interactive",
						 "context": {"from": "56900000000", "id": "%s"},
						 "interactive": {"type": "button_reply", "button_reply": {"id": "%s", "title": "%s"}}}"""
						.formatted(idEntrante, reloj.instant().getEpochSecond(), wamidOriginal, payload, payload)
				: """
						{"from": "56911111111", "id": "%s", "timestamp": "%d", "type": "button",
						 "context": {"from": "56900000000", "id": "%s"},
						 "button": {"payload": "%s", "text": "%s"}}"""
						.formatted(idEntrante, reloj.instant().getEpochSecond(), wamidOriginal, payload, payload);
		String cuerpo = """
				{"object": "whatsapp_business_account", "entry": [{"id": "WABA", "changes": [{
				 "field": "messages", "value": {"messaging_product": "whatsapp",
				  "metadata": {"display_phone_number": "56900000000", "phone_number_id": "106540352242922"},
				  "messages": [%s]}}]}]}""".formatted(mensaje);
		mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.header("X-Hub-Signature-256", firma(cuerpo)))
				.andExpect(status().isOk());
	}

	/** @return etapa actual en conversacion_whatsapp para ese intento */
	private String etapa(UUID intento) {
		return jdbc.sql("SELECT etapa FROM conversacion_whatsapp WHERE id_intento = :i")
				.param("i", intento).query(String.class).single();
	}

	/** @return una alerta del reloj en la base */
	private UUID alerta(UUID eventId, String severidad, boolean simulacro) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					es_simulacro, fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (:e, :a, 'h', 'RELOJ', 'CAIDA', :s, :sim, :t, :t) RETURNING id_alerta""")
				.param("e", eventId).param("a", asignacion).param("s", severidad).param("sim", simulacro)
				.param("t", Timestamp.from(INICIO)).query(UUID.class).single();
	}

	/** @return un contacto vinculado al paciente con WhatsApp y opt-in vigente */
	private UUID contacto(String nombre, String whatsapp, String tipo, int prioridad) {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, whatsapp, whatsapp_hash, fecha_opt_in)
				VALUES (:n, :w, :h, now()) RETURNING id_contacto""")
				.param("n", cifrado.cifrar(nombre, ColumnaCifrada.CONTACTO_NOMBRE))
				.param("w", cifrado.cifrar(whatsapp, ColumnaCifrada.CONTACTO_WHATSAPP))
				.param("h", "hash-conv-" + nombre)
				.query(UUID.class).single();
		return BaseDePrueba.vincular(jdbc, paciente, persona, tipo, prioridad);
	}

	/** @return un intento de WhatsApp ya enviado con su wamid */
	private UUID intentoWhatsapp(UUID alerta, UUID vinculo, String wamid) {
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda, id_externo,
					fecha_hora_envio)
				VALUES (gen_random_uuid(), :a, :v, 'WHATSAPP', 1, :w, :t) RETURNING id_intento""")
				.param("a", alerta).param("v", vinculo).param("w", wamid).param("t", Timestamp.from(INICIO))
				.query(UUID.class).single();
	}

	/** @return firma X-Hub-Signature-256 calculada como Meta */
	private static String firma(String cuerpo) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
	}

}
