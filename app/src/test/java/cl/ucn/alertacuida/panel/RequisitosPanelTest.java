package cl.ucn.alertacuida.panel;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
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
 * Panel general de la portada visto desde afuera (cronograma 3.4.1 y decisiones del 19-11-2026). Sin Docker se
 * omite.
 *
 * <ul>
 * <li>A1 Alerta abierta = igual que la lista: no cerrada por el equipo (en seguimiento cuenta), sin canceladas
 * ni simulacros; salud y técnicas. Totales de abiertas, sin respuesta y en seguimiento.</li>
 * <li>A2 Una fila por usuario activo (sin los dados de baja), con su reloj vigente, su semáforo y sus alertas
 * abiertas; sin reloj, vacío. Totales de relojes en rojo y amarillo.</li>
 * <li>A3 Exige ALERTAS:VER: también lo ve el rol PERSONAL.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosPanelTest {

	private static final String RUN_PERSONAL = "22.222.222-2";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;
	@Autowired
	private CifradoCampos cifrado;

	private final Instant ahora = Instant.now();
	private int pacientes;
	private String token;

	/** Base vacía y una persona de rol PERSONAL con sesión (solo tiene permisos de alertas). */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "registro_auditoria",
				"usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES ('PERSONAL', 'cifrado', :hash, 'Diego', 'diego@muni.cl', :clave, true)""")
				.param("hash", hashRun.de(Run.de(RUN_PERSONAL))).param("clave", encoder.encode("Clave-de-Diego1"))
				.update();
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + RUN_PERSONAL + "\", \"contrasena\": \"Clave-de-Diego1\"}"))
				.andReturn().getResponse().getContentAsString();
		token = JsonPath.read(respuesta, "$.token");
	}

	/** A1: abiertas, sin respuesta y en seguimiento; cerradas, canceladas y simulacros no cuentan. */
	@Test
	void losTotalesDeAlertas() throws Exception {
		UUID asignacion = asignacionDe(paciente("Soto", false), "R-PAN-1");
		alerta(asignacion, "CAIDA", false, false, false);
		alerta(asignacion, "BATERIA_BAJA", false, false, false);
		UUID sinRespuesta = alerta(asignacion, "FC", false, false, false);
		jdbc.sql("UPDATE alerta SET fecha_hora_sin_respuesta = now() WHERE id_alerta = :a").param("a", sinRespuesta)
				.update();
		atencion(alerta(asignacion, "SPO2", false, false, false), "POSITIVA", false);
		atencion(alerta(asignacion, "SPO2", false, false, false), "FALSA_ALARMA", false);
		alerta(asignacion, "FC", true, false, false);
		alerta(asignacion, "FC", false, true, false);

		panel().andExpect(status().isOk())
				.andExpect(jsonPath("$.totales.alertasAbiertas").value(4))
				.andExpect(jsonPath("$.totales.sinRespuesta").value(1))
				.andExpect(jsonPath("$.totales.enSeguimiento").value(1))
				.andExpect(jsonPath("$.usuarios[0].alertasAbiertas").value(4));
	}

	/** A2: los activos con su reloj vigente y su semáforo; el dado de baja no aparece. */
	@Test
	void unaFilaPorUsuarioActivo() throws Exception {
		UUID conReloj = paciente("Soto", false);
		asignacionDe(conReloj, "R-PAN-2");
		paciente("Pérez", false);
		paciente("Baja", true);

		panel().andExpect(jsonPath("$.usuarios", hasSize(2)))
				.andExpect(jsonPath("$.usuarios[?(@.apellidos == 'Soto')].reloj.identificador")
						.value(contains("R-PAN-2")))
				.andExpect(jsonPath("$.usuarios[?(@.apellidos == 'Soto')].reloj.semaforo").value(contains("ROJO")))
				.andExpect(jsonPath("$.usuarios[?(@.apellidos == 'Pérez')].reloj").value(contains((Object) null)))
				.andExpect(jsonPath("$.totales.relojesRojo").value(1))
				.andExpect(jsonPath("$.totales.relojesAmarillo").value(0));
	}

	/** A3: sin sesión, 401. */
	@Test
	void sinSesion401() throws Exception {
		mvc.perform(get("/api/panel")).andExpect(status().isUnauthorized());
	}

	// ---------- Apoyo ----------

	/** Pide el panel con la sesión de PERSONAL. */
	private ResultActions panel() throws Exception {
		return mvc.perform(get("/api/panel").header("Authorization", "Bearer " + token));
	}

	/** @return un paciente con ese apellido, activo o dado de baja */
	private UUID paciente(String apellidos, boolean deBaja) {
		pacientes++;
		return jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, nombres, apellidos, fecha_consentimiento, fecha_ingreso,
					fecha_baja)
				VALUES (:rut, :hash, :nombres, :apellidos, CURRENT_DATE, CURRENT_DATE, :baja) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("rut-" + pacientes, ColumnaCifrada.PACIENTE_RUT))
				.param("hash", "hash-pan-" + pacientes)
				.param("nombres", cifrado.cifrar("Nombre", ColumnaCifrada.PACIENTE_NOMBRES))
				.param("apellidos", cifrado.cifrar(apellidos, ColumnaCifrada.PACIENTE_APELLIDOS))
				.param("baja", deBaja ? java.sql.Date.valueOf(java.time.LocalDate.now()) : null)
				.query(UUID.class).single();
	}

	/** @return el vínculo de un reloj nuevo con el paciente, desde hace 10 horas y sin sincronizar (rojo) */
	private UUID asignacionDe(UUID idPaciente, String identificador) {
		UUID reloj = jdbc.sql("INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:i, 'OPERATIVO') "
				+ "RETURNING id_dispositivo").param("i", identificador).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, :hash) RETURNING id_asignacion""")
				.param("r", reloj).param("p", idPaciente)
				.param("inicio", Timestamp.from(ahora.minus(Duration.ofHours(10))))
				.param("hash", "hash-token-" + identificador).query(UUID.class).single();
	}

	/** @return una alerta del reloj de ese vínculo, recibida hace un minuto */
	private UUID alerta(UUID asignacion, String tipo, boolean cancelada, boolean simulacro, boolean deServidor) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					fecha_hora_deteccion, fecha_hora_recepcion, fecha_hora_cancelacion, es_simulacro)
				VALUES (gen_random_uuid(), :a, :h, :o, :t, 'MEDIA', :d, :d, :c, :s) RETURNING id_alerta""")
				.param("a", asignacion).param("h", deServidor ? null : "h")
				.param("o", deServidor ? "SERVIDOR" : "RELOJ")
				.param("t", tipo).param("d", Timestamp.from(ahora.minusSeconds(60)))
				.param("c", cancelada ? Timestamp.from(ahora.minusSeconds(30)) : null).param("s", simulacro)
				.query(UUID.class).single();
	}

	/** Cierra la alerta con esa clasificación; una POSITIVA queda en seguimiento salvo que se cierre. */
	private void atencion(UUID alerta, String clasificacion, boolean seguimientoCerrado) {
		UUID usuario = jdbc.sql("SELECT id_usuario FROM usuario LIMIT 1").query(UUID.class).single();
		jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, id_usuario_responsable, fecha_hora_toma, id_usuario_resolutor,
					fecha_hora_cierre, clasificacion, observaciones_cierre, fecha_cierre_seguimiento)
				VALUES (:a, :u, now(), :u, now(), :c, 'cifrado', :s)""")
				.param("a", alerta).param("u", usuario).param("c", clasificacion)
				.param("s", seguimientoCerrado ? Timestamp.from(ahora) : null).update();
	}

}
