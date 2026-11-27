package cl.ucn.alertacuida.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * "Llamar al paciente" y "Llegué donde el paciente" en la página del enlace del correo de alerta, y el cierre
 * que el equipo completa después. Sale de las decisiones del 24-11-2026. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Mientras la alerta está abierta, la página trae el número de la SIM del reloj para llamar al paciente;
 * cerrada, ya no.</li>
 * <li>R2 Presionar "Llamar al paciente" queda registrado una vez (la primera) y aparece en la línea de
 * tiempo.</li>
 * <li>R3 Solo quien respondió "Voy en camino" puede avisar su llegada (409 si no).</li>
 * <li>R4 La llegada se registra y resuelve la alerta (por el sistema: sin responsable ni clasificación), deja el
 * aviso ALERTA_RESUELTA para el reloj y la ficha muestra quién llegó. Repetirla no cambia nada.</li>
 * <li>R5 Si el equipo ya la había resuelto, la llegada se registra sin tocar ese cierre.</li>
 * <li>R6 El equipo completa el cierre de una alerta resuelta por la llegada: clasificación y observaciones, una
 * sola vez; POSITIVA abre el seguimiento con quien completa como responsable. Una alerta resuelta por una
 * persona no se completa (409).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosLlamadaYLlegadaTest {

	private static final Instant DETECCION = Instant.parse("2026-11-24T15:00:00Z");
	private static final String SIM = "+56987654321";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private FirmaRespuesta firma;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	private UUID alerta;
	private UUID intento;
	private String token;

	/** Base vacía; Rosa con su reloj (SIM {@value #SIM}), una caída grave y un correo enviado a su hija. */
	@BeforeEach
	void preparar() {
		BaseDePrueba.vaciar(jdbc);
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-llegada', 'Rosa', CURRENT_DATE - 10, CURRENT_DATE - 10) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, numero_sim, estado) VALUES ('R-LLEGADA', :sim, 'OPERATIVO')
				RETURNING id_dispositivo""").param("sim", SIM).query(UUID.class).single();
		UUID asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, 'hash-token-llegada') RETURNING id_asignacion""")
				.param("r", reloj).param("p", paciente).param("inicio", Timestamp.from(DETECCION.minusSeconds(86_400)))
				.query(UUID.class).single();
		alerta = jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', 'CAIDA', 'GRAVE', :t, :t) RETURNING id_alerta""")
				.param("a", asignacion).param("t", Timestamp.from(DETECCION)).query(UUID.class).single();
		intento = correoEnviado(vinculo(paciente));
		token = firma.firmar(intento);
	}

	// ---------- R1 ----------

	/** R1: abierta trae el número de la SIM; resuelta, no. */
	@Test
	void elNumeroSoloSeMuestraConLaAlertaAbierta() throws Exception {
		consultar().andExpect(status().isOk()).andExpect(jsonPath("$.telefonoPaciente").value(SIM))
				.andExpect(jsonPath("$.llegada").value(nullValue()));

		resueltaPorElEquipo();

		consultar().andExpect(jsonPath("$.telefonoPaciente").value(nullValue()));
	}

	// ---------- R2 ----------

	/** R2: la llamada queda registrada una sola vez, con la hora de la primera. */
	@Test
	void laLlamadaQuedaRegistradaUnaVez() throws Exception {
		accion("llamada").andExpect(status().isNoContent());
		Instant primera = fecha("fecha_hora_llamada");

		accion("llamada").andExpect(status().isNoContent());

		assertThat(primera).isNotNull();
		assertThat(fecha("fecha_hora_llamada")).isEqualTo(primera);
	}

	/** R2: un token inventado no registra nada. */
	@Test
	void unTokenInventadoNoRegistraLaLlamada() throws Exception {
		mvc.perform(post("/api/respuestas-contacto/llamada").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"inventado.abc\"}")).andExpect(status().isBadRequest());
	}

	// ---------- R3 ----------

	/** R3: sin haber dicho "voy", la llegada responde 409 y no resuelve nada. */
	@Test
	void sinVoyNoSePuedeAvisarLaLlegada() throws Exception {
		accion("llegada").andExpect(status().isConflict());
		responder("NO_PUEDO");
		accion("llegada").andExpect(status().isConflict());

		assertThat(fecha("fecha_hora_llegada")).isNull();
		assertThat(estadoAtencion()).isEqualTo("SIN_ASIGNAR");
	}

	// ---------- R4 ----------

	/** R4: la llegada resuelve la alerta por el sistema, avisa al reloj y la ficha la muestra. */
	@Test
	void laLlegadaResuelveLaAlerta() throws Exception {
		accion("llamada");
		responder("VOY_EN_CAMINO");

		accion("llegada").andExpect(status().isOk()).andExpect(jsonPath("$.llegada").exists())
				.andExpect(jsonPath("$.cerrada").value(true));

		Instant llegada = fecha("fecha_hora_llegada");
		assertThat(llegada).isNotNull();
		assertThat(estadoAtencion()).isEqualTo("RESUELTA");
		assertThat(jdbc.sql("""
				SELECT fecha_hora_cierre = :l AND id_usuario_resolutor IS NULL AND clasificacion IS NULL
				FROM atencion_alerta WHERE id_alerta = :a""")
				.param("l", Timestamp.from(llegada)).param("a", alerta).query(Boolean.class).single()).isTrue();
		assertThat(jdbc.sql("SELECT count(*) FROM aviso_fcm WHERE id_alerta = :a AND tipo = 'CAMBIO_ESTADO_ALERTA'")
				.param("a", alerta).query(Integer.class).single()).isEqualTo(2);

		ficha(sesion()).andExpect(status().isOk())
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", hasItem("LLAMADA_PACIENTE")))
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", hasItem("LLEGADA_CONTACTO")))
				.andExpect(jsonPath("$.lineaTiempo[?(@.tipo == 'ALERTA_RESUELTA')].contacto", hasItem("Hija")));

		accion("llegada").andExpect(status().isOk());
		assertThat(fecha("fecha_hora_llegada")).isEqualTo(llegada);
	}

	// ---------- R5 ----------

	/** R5: si el equipo ya la resolvió, la llegada se guarda y el cierre del equipo queda igual. */
	@Test
	void conLaAlertaYaResueltaSoloSeRegistraLaLlegada() throws Exception {
		responder("VOY_EN_CAMINO");
		UUID quien = resueltaPorElEquipo();

		accion("llegada").andExpect(status().isOk());

		assertThat(fecha("fecha_hora_llegada")).isNotNull();
		assertThat(jdbc.sql("SELECT id_usuario_resolutor FROM atencion_alerta WHERE id_alerta = :a")
				.param("a", alerta).query(UUID.class).single()).isEqualTo(quien);
	}

	// ---------- R6 ----------

	/** R6: el equipo completa el cierre una sola vez; FALSA_ALARMA deja la alerta resuelta. */
	@Test
	void elEquipoCompletaElCierreUnaSolaVez() throws Exception {
		responder("VOY_EN_CAMINO");
		accion("llegada");
		String sesion = sesion();

		completar(sesion, "FALSA_ALARMA", "La hija llegó y estaba bien").andExpect(status().isNoContent());

		assertThat(jdbc.sql("SELECT clasificacion FROM atencion_alerta WHERE id_alerta = :a")
				.param("a", alerta).query(String.class).single()).isEqualTo("FALSA_ALARMA");
		assertThat(estadoAtencion()).isEqualTo("RESUELTA");
		completar(sesion, "POSITIVA", "Otra vez").andExpect(status().isConflict());
	}

	/** R6: POSITIVA abre el seguimiento, con quien completa como responsable. */
	@Test
	void completarComoPositivaAbreElSeguimiento() throws Exception {
		responder("VOY_EN_CAMINO");
		accion("llegada");

		completar(sesion(), "POSITIVA", "Se cayó; la hija la acompaña").andExpect(status().isNoContent());

		assertThat(estadoAtencion()).isEqualTo("EN_SEGUIMIENTO");
		assertThat(jdbc.sql("SELECT id_usuario_responsable IS NOT NULL FROM atencion_alerta WHERE id_alerta = :a")
				.param("a", alerta).query(Boolean.class).single()).isTrue();
	}

	/** R6: sin observaciones se rechaza, y una alerta resuelta por una persona no se completa. */
	@Test
	void completarExigeObservacionesYUnCierreDelSistema() throws Exception {
		responder("VOY_EN_CAMINO");
		accion("llegada");
		String sesion = sesion();
		completar(sesion, "FALSA_ALARMA", " ").andExpect(status().isBadRequest());

		jdbc.sql("DELETE FROM atencion_alerta").update();
		resueltaPorElEquipo();
		completar(sesion, "FALSA_ALARMA", "Algo").andExpect(status().isConflict());
	}

	// ---------- Ayudas ----------

	/** @return la consulta pública de la página del enlace */
	private ResultActions consultar() throws Exception {
		return mvc.perform(post("/api/respuestas-contacto/consulta").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\"}"));
	}

	/** Responde con un botón de la página. */
	private void responder(String respuesta) throws Exception {
		mvc.perform(post("/api/respuestas-contacto").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\", \"respuesta\": \"" + respuesta + "\"}"))
				.andExpect(status().isOk());
	}

	/** @return "llamada" o "llegada" desde la página pública */
	private ResultActions accion(String cual) throws Exception {
		return mvc.perform(post("/api/respuestas-contacto/" + cual).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\"}"));
	}

	/** Completa el cierre con esa sesión. */
	private ResultActions completar(String sesion, String clasificacion, String observaciones) throws Exception {
		return mvc.perform(post("/api/alertas/" + alerta + "/completar-cierre")
				.header("Authorization", "Bearer " + sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"clasificacion\": \"" + clasificacion + "\", \"observaciones\": \"" + observaciones
						+ "\"}"));
	}

	/** @return la ficha de la alerta */
	private ResultActions ficha(String sesion) throws Exception {
		return mvc.perform(get("/api/alertas/" + alerta).header("Authorization", "Bearer " + sesion));
	}

	/** @return esa fecha del aviso por correo, o null */
	private Instant fecha(String columna) {
		// list() y no single(): single() no acepta una columna en NULL
		Timestamp valor = jdbc.sql("SELECT " + columna + " FROM registro_intento WHERE id_intento = :i")
				.param("i", intento).query((fila, n) -> fila.getTimestamp(1)).list().getFirst();
		return valor == null ? null : valor.toInstant();
	}

	/** @return el estado de atención de la alerta */
	private String estadoAtencion() {
		return jdbc.sql("SELECT estado_atencion FROM alerta_estado WHERE id_alerta = :a").param("a", alerta)
				.query(String.class).single();
	}

	/** @return la cuenta que la resolvió: alguien del equipo, con clasificación */
	private UUID resueltaPorElEquipo() {
		UUID cuenta = cuenta("COORDINADOR", "22.222.222-2");
		jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, id_usuario_responsable, fecha_hora_toma, fecha_hora_cierre,
					id_usuario_resolutor, clasificacion, observaciones_cierre)
				VALUES (:a, :u, now(), now(), :u, 'FALSA_ALARMA', :obs)""")
				.param("a", alerta).param("u", cuenta)
				.param("obs", cifrado.cifrar("Resuelta", ColumnaCifrada.ATENCION_OBSERVACIONES)).update();
		return cuenta;
	}

	/** @return el token de acceso de un administrador nuevo (tiene ALERTAS:VER y ALERTAS:EDITAR) */
	private String sesion() throws Exception {
		cuenta("ADMINISTRADOR", "11.111.111-1");
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"11.111.111-1\", \"contrasena\": \"Clave-de-prueba1\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(cuerpo, "$.token");
	}

	/** @return una cuenta activa con ese rol y la contraseña "Clave-de-prueba1" */
	private UUID cuenta(String rol, String run) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, 'Persona', :correo, :clave, true) RETURNING id_usuario""")
				.param("rol", rol).param("hash", hashRun.de(Run.de(run)))
				.param("correo", rol.toLowerCase() + "@muni.cl")
				.param("clave", encoder.encode("Clave-de-prueba1"))
				.query(UUID.class).single();
	}

	/** @return el vínculo de la hija, con correo, en la lista de Rosa */
	private UUID vinculo(UUID paciente) {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, correo, correo_hash) VALUES (:nombre, :correo, 'hash-c-hija-llegada')
				RETURNING id_contacto""")
				.param("nombre", cifrado.cifrar("Hija", ColumnaCifrada.CONTACTO_NOMBRE))
				.param("correo", cifrado.cifrar("hija@correo.cl", ColumnaCifrada.CONTACTO_CORREO))
				.query(UUID.class).single();
		return BaseDePrueba.vincular(jdbc, paciente, persona, "OTRO", 1);
	}

	/** @return el aviso por correo, ya enviado, a ese vínculo en la ronda 1 */
	private UUID correoEnviado(UUID vinculo) {
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda,
				fecha_hora_envio) VALUES (gen_random_uuid(), :a, :v, 'CORREO', 1, :t) RETURNING id_intento""")
				.param("a", alerta).param("v", vinculo).param("t", Timestamp.from(DETECCION.plusSeconds(40)))
				.query(UUID.class).single();
	}

}
