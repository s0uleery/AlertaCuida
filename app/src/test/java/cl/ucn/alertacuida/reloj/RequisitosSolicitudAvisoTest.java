package cl.ucn.alertacuida.reloj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.EncolarCorreos;
import cl.ucn.alertacuida.application.aviso.EnviarCorreosPendientes;
import cl.ucn.alertacuida.application.aviso.EnviarWhatsappPendientes;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
 * Solicitud de aviso del reloj, POST /api/reloj/v1/alertas/{eventId}/avisos, contra la base real. Sale de
 * docs/api-reloj.yaml (SolicitudAviso, RespuestaAviso), docs/api-reloj.md y la decisión del 13-11-2026
 * sobre WhatsApp antes de la 2.9. Sin Docker se omite.
 * <ul>
 * <li>R1 Un aviso aceptado queda en la bandeja y sale por su canal; la alerta pasa a AVISANDO_CONTACTO.</li>
 * <li>R2 No se envía dos veces: ni con el mismo eventId ni con el mismo contacto, canal y ronda.</li>
 * <li>R3 Un rechazo es 200 con RECHAZADO y su motivo, y trae el estado de la alerta.</li>
 * <li>R4 Con la alerta terminada, ALERTA_TERMINADA y detenerEscalamiento true.</li>
 * <li>R5 Errores del contrato: 401 sin token, 404 ALERTA_DESCONOCIDA, 400 MENSAJE_INVALIDO.</li>
 * <li>R6 Sin canal de WhatsApp configurado, el aviso falla al tiro y sale el correo de respaldo.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosSolicitudAvisoTest {

	private static final String TOKEN = "token-reloj-avisos";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private SecretosUnSoloUso secretos;
	@Autowired
	private EnviarCorreosPendientes enviarCorreos;
	@Autowired
	private EnviarWhatsappPendientes enviarWhatsapp;
	@Autowired
	private EncolarCorreos encolarCorreos;

	private final Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
	private UUID eventAlerta;
	private UUID idAlerta;
	private UUID hija;
	private UUID vecino;
	private UUID sobrino;

	/**
	 * Base vacía; Rosa con su reloj y una caída grave recién recibida. En su lista: la hija (WhatsApp con
	 * opt-in y correo), el vecino (WhatsApp sin opt-in) y el sobrino (correo, desactivado).
	 */
	@BeforeEach
	void preparar() {
		jdbc.sql("DELETE FROM paciente").update();
		jdbc.sql("DELETE FROM contacto").update();
		jdbc.sql("DELETE FROM smartwatch").update();
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-avisos', 'Rosa', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID asignacion = asignacion(paciente);
		eventAlerta = UUID.randomUUID();
		idAlerta = jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (:e, :a, 'h', 'RELOJ', 'CAIDA', 'GRAVE', :t, :t) RETURNING id_alerta""")
				.param("e", eventAlerta).param("a", asignacion).param("t", Timestamp.from(ahora))
				.query(UUID.class).single();
		hija = vinculo(paciente, "Hija", "+56911111111", "hija@correo.cl", true, true, 1);
		vecino = vinculo(paciente, "Vecino", "+56922222222", null, false, true, 2);
		sobrino = vinculo(paciente, "Sobrino", null, "sobrino@correo.cl", false, false, 3);
	}

	/** R1: el aviso por correo se acepta, queda pendiente, la alerta avanza y el correo sale. */
	@Test
	void unAvisoPorCorreoSeAceptaYSale() throws Exception {
		String cuerpo = pedir(UUID.randomUUID(), hija, "CORREO", 1).andExpect(status().isOk())
				.andExpect(jsonPath("$.resultado").value("ACEPTADO"))
				.andExpect(jsonPath("$.motivo").doesNotExist())
				.andExpect(jsonPath("$.idIntento").isNotEmpty())
				.andExpect(jsonPath("$.estadoAlerta.estado").value("AVISANDO_CONTACTO"))
				.andExpect(jsonPath("$.estadoAlerta.detenerEscalamiento").value(false))
				.andReturn().getResponse().getContentAsString();
		UUID intento = UUID.fromString(JsonPath.read(cuerpo, "$.idIntento"));
		assertThat(columna(intento, "fecha_hora_proximo_envio IS NOT NULL")).isTrue();

		enviarCorreos.enviar();

		assertThat(columna(intento, "fecha_hora_envio IS NOT NULL")).isTrue();
	}

	/** R2: el mismo eventId, o el mismo contacto, canal y ronda con otro eventId, no crean otro aviso. */
	@Test
	void noSeEnviaDosVeces() throws Exception {
		UUID evento = UUID.randomUUID();
		String primero = idIntento(pedir(evento, hija, "CORREO", 1));

		assertThat(idIntento(pedir(evento, hija, "CORREO", 1))).isEqualTo(primero);
		assertThat(idIntento(pedir(UUID.randomUUID(), hija, "CORREO", 1))).isEqualTo(primero);
		assertThat(avisos()).isEqualTo(1);

		pedir(UUID.randomUUID(), hija, "CORREO", 2).andExpect(jsonPath("$.resultado").value("ACEPTADO"));
		assertThat(avisos()).isEqualTo(2);
	}

	/** R3: cada rechazo trae su motivo y el estado de la alerta, sin crear avisos. */
	@Test
	void losRechazosTraenSuMotivo() throws Exception {
		rechazo(vecino, "WHATSAPP", "SIN_OPT_IN");
		rechazo(vecino, "CORREO", "CANAL_NO_DISPONIBLE");
		rechazo(sobrino, "CORREO", "CONTACTO_INACTIVO");
		rechazo(UUID.randomUUID(), "CORREO", "CONTACTO_DESCONOCIDO");

		assertThat(avisos()).isZero();
	}

	/** R4: con la alerta cancelada (o confirmada, o resuelta) se rechaza y se pide detener el escalamiento. */
	@Test
	void conLaAlertaTerminadaSePideDetenerse() throws Exception {
		jdbc.sql("UPDATE alerta SET fecha_hora_cancelacion = :t WHERE id_alerta = :a")
				.param("t", Timestamp.from(ahora)).param("a", idAlerta).update();

		pedir(UUID.randomUUID(), hija, "CORREO", 1).andExpect(status().isOk())
				.andExpect(jsonPath("$.resultado").value("RECHAZADO"))
				.andExpect(jsonPath("$.motivo").value("ALERTA_TERMINADA"))
				.andExpect(jsonPath("$.estadoAlerta.detenerEscalamiento").value(true));
		assertThat(avisos()).isZero();
	}

	/** R5: sin token 401; alerta desconocida 404 ALERTA_DESCONOCIDA; ronda 3 o sin canal 400 MENSAJE_INVALIDO. */
	@Test
	void erroresDelContrato() throws Exception {
		mvc.perform(post("/api/reloj/v1/alertas/" + eventAlerta + "/avisos").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(UUID.randomUUID(), hija, "CORREO", 1))).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/reloj/v1/alertas/" + UUID.randomUUID() + "/avisos")
				.header("Authorization", "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(UUID.randomUUID(), hija, "CORREO", 1)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ALERTA_DESCONOCIDA"));
		pedir(UUID.randomUUID(), hija, "CORREO", 3).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("MENSAJE_INVALIDO"));
		mvc.perform(post("/api/reloj/v1/alertas/" + eventAlerta + "/avisos").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\": \"" + UUID.randomUUID() + "\", \"idContacto\": \"" + hija
						+ "\", \"ronda\": 1}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("MENSAJE_INVALIDO"));
	}

	/** R6: sin canal de WhatsApp, el aviso falla al tiro y se encola el correo de respaldo a la misma persona. */
	@Test
	void sinCanalDeWhatsappSaleElCorreoDeRespaldo() throws Exception {
		UUID intento = UUID.fromString(idIntento(pedir(UUID.randomUUID(), hija, "WHATSAPP", 1)
				.andExpect(jsonPath("$.resultado").value("ACEPTADO"))));

		enviarWhatsapp.enviar();
		assertThat(columna(intento, "fecha_hora_fallo IS NOT NULL")).isTrue();
		assertThat(columna(intento, "reintentos = 0")).isTrue();

		encolarCorreos.encolar();
		Integer respaldos = jdbc.sql("""
				SELECT count(*) FROM registro_intento
				WHERE id_alerta = :a AND id_contacto_paciente = :c AND canal = 'CORREO' AND ronda = 1""")
				.param("a", idAlerta).param("c", hija).query(Integer.class).single();
		assertThat(respaldos).isEqualTo(1);
	}

	/** Pide el aviso y revisa que sea un rechazo con ese motivo y el estado de la alerta. */
	private void rechazo(UUID contacto, String canal, String motivo) throws Exception {
		pedir(UUID.randomUUID(), contacto, canal, 1).andExpect(status().isOk())
				.andExpect(jsonPath("$.resultado").value("RECHAZADO"))
				.andExpect(jsonPath("$.motivo").value(motivo))
				.andExpect(jsonPath("$.idIntento").doesNotExist())
				.andExpect(jsonPath("$.estadoAlerta.estado").value("RECIBIDA"));
	}

	/** Pide al servidor avisar a un contacto de la alerta de Rosa. */
	private ResultActions pedir(UUID eventId, UUID contacto, String canal, int ronda) throws Exception {
		return mvc.perform(post("/api/reloj/v1/alertas/" + eventAlerta + "/avisos")
				.header("Authorization", "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(eventId, contacto, canal, ronda)));
	}

	/** @return el cuerpo JSON de una solicitud de aviso */
	private static String cuerpo(UUID eventId, UUID contacto, String canal, int ronda) {
		return """
				{"eventId": "%s", "idContacto": "%s", "canal": "%s", "ronda": %d}"""
				.formatted(eventId, contacto, canal, ronda);
	}

	/** @return el idIntento de una respuesta aceptada */
	private static String idIntento(ResultActions respuesta) throws Exception {
		return JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.idIntento");
	}

	/** @return cuántos avisos a contactos del paciente tiene la alerta */
	private int avisos() {
		return jdbc.sql("""
				SELECT count(*) FROM registro_intento WHERE id_alerta = :a AND id_contacto_paciente IS NOT NULL""")
				.param("a", idAlerta).query(Integer.class).single();
	}

	/** @return el valor de una condición sobre la fila del aviso */
	private boolean columna(UUID intento, String condicion) {
		return jdbc.sql("SELECT " + condicion + " FROM registro_intento WHERE id_intento = :i")
				.param("i", intento).query(Boolean.class).single();
	}

	/** @return el vínculo vigente de un reloj nuevo con el paciente, con el token de las pruebas */
	private UUID asignacion(UUID paciente) {
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-AVISOS', 'OPERATIVO')
				RETURNING id_dispositivo""")
				.query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:reloj, :paciente, :inicio, :hash) RETURNING id_asignacion""")
				.param("reloj", reloj).param("paciente", paciente)
				.param("inicio", Timestamp.from(ahora.minus(1, ChronoUnit.DAYS)))
				.param("hash", secretos.hash(TOKEN))
				.query(UUID.class).single();
	}

	/** @return el vínculo de una persona en la lista del paciente, con los canales y opt-in indicados */
	private UUID vinculo(UUID paciente, String nombre, String whatsapp, String correo, boolean optIn,
			boolean activo, int prioridad) {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, whatsapp, whatsapp_hash, correo, correo_hash, fecha_opt_in)
				VALUES (:nombre, :whatsapp, :hashW, :correo, :hashC, :optIn) RETURNING id_contacto""")
				.param("nombre", cifrado.cifrar(nombre, ColumnaCifrada.CONTACTO_NOMBRE))
				.param("whatsapp", whatsapp == null ? null : cifrado.cifrar(whatsapp, ColumnaCifrada.CONTACTO_WHATSAPP))
				.param("hashW", whatsapp == null ? null : "hash-w-" + nombre)
				.param("correo", correo == null ? null : cifrado.cifrar(correo, ColumnaCifrada.CONTACTO_CORREO))
				.param("hashC", correo == null ? null : "hash-c-" + nombre)
				.param("optIn", optIn ? Timestamp.from(ahora.minus(1, ChronoUnit.DAYS)) : null)
				.query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad, activo)
				VALUES (:paciente, :persona, 'OTRO', :prioridad, :activo) RETURNING id_contacto_paciente""")
				.param("paciente", paciente).param("persona", persona).param("prioridad", prioridad)
				.param("activo", activo)
				.query(UUID.class).single();
	}

}
