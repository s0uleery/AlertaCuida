package cl.ucn.alertacuida.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Respuesta de un contacto del paciente desde los enlaces de su correo, contra la base real y por las rutas
 * HTTP públicas. Sale de las decisiones del 12-11-2026 y de docs/alertas.md ("cuentan igual que los botones
 * del WhatsApp"). Sin Docker se omite.
 * <ul>
 * <li>R1 Las rutas son públicas: el contacto no tiene cuenta. El enlace del correo lleva un token que
 * sirve con ellas; uno inventado o el de un aviso a una cuenta del equipo responde 400.</li>
 * <li>R2 Abrir el enlace no registra nada: muestra folio, alias, tipo, gravedad y hora.</li>
 * <li>R3 "Voy" confirma la alerta y deja el aviso ALERTA_CONFIRMADA para el reloj; "No puedo" queda
 * registrado sin confirmar la alerta.</li>
 * <li>R4 Vale solo la primera respuesta.</li>
 * <li>R5 Con la alerta cancelada (o resuelta) no se registra y la respuesta lo indica.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosRespuestaContactoTest {

	private static final Instant DETECCION = Instant.parse("2026-11-12T15:00:00Z");

	/** Token del enlace del correo: lo que va después de "#". */
	private static final Pattern ENLACE = Pattern.compile("/responder#([A-Za-z0-9_.-]+)");

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private PlantillasCorreo plantillas;

	private UUID alerta;
	private UUID intento;
	private String token;

	/** Base vacía; Rosa con su reloj, una caída grave (#numero) y un correo ya enviado a su hija. */
	@BeforeEach
	void preparar() {
		jdbc.sql("DELETE FROM paciente").update();
		jdbc.sql("DELETE FROM contacto").update();
		jdbc.sql("DELETE FROM smartwatch").update();
		jdbc.sql("DELETE FROM usuario WHERE rut_hash LIKE 'hash-respuesta-%'").update();
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-respuesta', 'Rosa', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID asignacion = asignacion(paciente);
		alerta = jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', 'CAIDA', 'GRAVE', :t, :t) RETURNING id_alerta""")
				.param("a", asignacion).param("t", Timestamp.from(DETECCION))
				.query(UUID.class).single();
		intento = correoEnviado(vinculo(paciente));
		token = tokenDelCorreo(intento);
	}

	/** R1 y R2: el enlace del correo abre la alerta sin iniciar sesión, y abrirla no registra nada. */
	@Test
	void elEnlaceDelCorreoMuestraLaAlertaSinRegistrar() throws Exception {
		consultar(token).andExpect(status().isOk())
				.andExpect(jsonPath("$.numero").value(numero()))
				.andExpect(jsonPath("$.alias").value("Rosa"))
				.andExpect(jsonPath("$.tipo").value("CAIDA"))
				.andExpect(jsonPath("$.severidad").value("GRAVE"))
				.andExpect(jsonPath("$.fechaHora").value("2026-11-12T15:00:00Z"))
				.andExpect(jsonPath("$.respuesta").doesNotExist())
				.andExpect(jsonPath("$.cerrada").value(false));

		assertThat(respuestaGuardada()).isNull();
	}

	/** R1: un token inventado responde 400, en la consulta y en la respuesta. */
	@Test
	void tokenInventadoResponde400() throws Exception {
		consultar("inventado.abc").andExpect(status().isBadRequest());
		responder("inventado.abc", "VOY_EN_CAMINO").andExpect(status().isBadRequest());
	}

	/** R1: el token de un aviso a una cuenta del equipo no sirve: solo un contacto del paciente confirma. */
	@Test
	void avisoAlEquipoNoSirve() throws Exception {
		consultar(tokenDelCorreo(correoAlEquipo())).andExpect(status().isBadRequest());
	}

	/** R3: "voy" confirma la alerta y deja el aviso para que el reloj deje de escalar. */
	@Test
	void voyConfirmaLaAlertaYAvisaAlReloj() throws Exception {
		responder(token, "VOY_EN_CAMINO").andExpect(status().isOk())
				.andExpect(jsonPath("$.respuesta").value("VOY_EN_CAMINO"))
				.andExpect(jsonPath("$.fechaRespuesta").exists());

		assertThat(respuestaGuardada()).isEqualTo("VOY_EN_CAMINO");
		assertThat(estadoContacto()).isEqualTo("CONFIRMADA");
		assertThat(avisosConfirmada()).isEqualTo(1);
	}

	/** R3: "no puedo" queda registrado y la alerta sigue avisando a los demás. */
	@Test
	void noPuedoNoConfirmaLaAlerta() throws Exception {
		responder(token, "NO_PUEDO").andExpect(status().isOk())
				.andExpect(jsonPath("$.respuesta").value("NO_PUEDO"));

		assertThat(respuestaGuardada()).isEqualTo("NO_PUEDO");
		assertThat(estadoContacto()).isEqualTo("AVISANDO_CONTACTO");
		assertThat(avisosConfirmada()).isZero();
	}

	/** R4: la segunda respuesta no cambia la primera, y se devuelve la primera. */
	@Test
	void valeSoloLaPrimera() throws Exception {
		responder(token, "NO_PUEDO").andExpect(status().isOk());

		responder(token, "VOY_EN_CAMINO").andExpect(status().isOk())
				.andExpect(jsonPath("$.respuesta").value("NO_PUEDO"));
		assertThat(respuestaGuardada()).isEqualTo("NO_PUEDO");
		assertThat(avisosConfirmada()).isZero();
	}

	/** R5: con la alerta cancelada, no se registra y la respuesta dice que ya está cerrada. */
	@Test
	void alertaCanceladaNoRegistra() throws Exception {
		jdbc.sql("UPDATE alerta SET fecha_hora_cancelacion = :t WHERE id_alerta = :a")
				.param("t", Timestamp.from(DETECCION.plusSeconds(10))).param("a", alerta).update();

		responder(token, "VOY_EN_CAMINO").andExpect(status().isOk())
				.andExpect(jsonPath("$.cerrada").value(true))
				.andExpect(jsonPath("$.respuesta").doesNotExist());
		assertThat(respuestaGuardada()).isNull();
	}

	/** @return la consulta pública con ese token */
	private ResultActions consultar(String conToken) throws Exception {
		return mvc.perform(post("/api/respuestas-contacto/consulta").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + conToken + "\"}"));
	}

	/** @return la respuesta pública con ese token */
	private ResultActions responder(String conToken, String respuesta) throws Exception {
		return mvc.perform(post("/api/respuestas-contacto").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + conToken + "\",\"respuesta\":\"" + respuesta + "\"}"));
	}

	/** @return el token del enlace del correo que se redacta para ese aviso */
	private String tokenDelCorreo(UUID idIntento) {
		String texto = plantillas.redactar(new CorreoPendiente(idIntento, "hija@correo.cl", false, numero(), "Rosa",
				TipoEvento.CAIDA, Severidad.GRAVE, 0)).texto();
		Matcher enlace = ENLACE.matcher(texto);
		assertThat(enlace.find()).as("el correo lleva el enlace de respuesta").isTrue();
		return enlace.group(1);
	}

	/** @return el folio de la alerta */
	private long numero() {
		return jdbc.sql("SELECT numero FROM alerta WHERE id_alerta = :a").param("a", alerta).query(Long.class)
				.single();
	}

	/** @return lo que quedó en la respuesta del aviso, o null */
	private String respuestaGuardada() {
		return jdbc.sql("SELECT respuesta FROM registro_intento WHERE id_intento = :i").param("i", intento)
				.query(String.class).optional().orElse(null);
	}

	/** @return el estado de contacto de la alerta */
	private String estadoContacto() {
		return jdbc.sql("SELECT estado_contacto FROM alerta_estado WHERE id_alerta = :a").param("a", alerta)
				.query(String.class).single();
	}

	/** @return cuántos avisos ALERTA_CONFIRMADA hay para la alerta */
	private long avisosConfirmada() {
		return jdbc.sql("SELECT count(*) FROM aviso_fcm WHERE id_alerta = :a AND tipo = 'ALERTA_CONFIRMADA'")
				.param("a", alerta).query(Long.class).single();
	}

	/** @return el vínculo vigente de un reloj nuevo con el paciente */
	private UUID asignacion(UUID paciente) {
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-RESPUESTA', 'OPERATIVO')
				RETURNING id_dispositivo""")
				.query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:reloj, :paciente, :inicio, 'hash-token-respuesta') RETURNING id_asignacion""")
				.param("reloj", reloj).param("paciente", paciente)
				.param("inicio", Timestamp.from(DETECCION.minusSeconds(86_400)))
				.query(UUID.class).single();
	}

	/** @return el vínculo de la hija, con correo, en la lista de Rosa */
	private UUID vinculo(UUID paciente) {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, correo, correo_hash) VALUES (:nombre, :correo, 'hash-c-hija')
				RETURNING id_contacto""")
				.param("nombre", cifrado.cifrar("Hija", ColumnaCifrada.CONTACTO_NOMBRE))
				.param("correo", cifrado.cifrar("hija@correo.cl", ColumnaCifrada.CONTACTO_CORREO))
				.query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad)
				VALUES (:paciente, :persona, 'OTRO', 1) RETURNING id_contacto_paciente""")
				.param("paciente", paciente).param("persona", persona)
				.query(UUID.class).single();
	}

	/** @return el aviso por correo, ya enviado, a ese vínculo en la ronda 1 */
	private UUID correoEnviado(UUID vinculo) {
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda,
				fecha_hora_envio) VALUES (gen_random_uuid(), :a, :v, 'CORREO', 1, :t) RETURNING id_intento""")
				.param("a", alerta).param("v", vinculo).param("t", Timestamp.from(DETECCION.plusSeconds(40)))
				.query(UUID.class).single();
	}

	/** @return un aviso por correo, ya enviado, a una cuenta del equipo (contacto interno) */
	private UUID correoAlEquipo() {
		UUID cuenta = jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES ('PERSONAL', 'cifrado', 'hash-respuesta-ana', 'Ana', 'ana@muni.cl', 'clave', true)
				RETURNING id_usuario""")
				.query(UUID.class).single();
		UUID interno = jdbc.sql("INSERT INTO contacto (id_usuario) VALUES (:u) RETURNING id_contacto")
				.param("u", cuenta).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_interno, canal, fecha_hora_envio)
				VALUES (gen_random_uuid(), :a, :i, 'CORREO', :t) RETURNING id_intento""")
				.param("a", alerta).param("i", interno).param("t", Timestamp.from(DETECCION.plusSeconds(40)))
				.query(UUID.class).single();
	}

}
