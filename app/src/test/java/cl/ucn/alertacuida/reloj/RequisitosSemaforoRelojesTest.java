package cl.ucn.alertacuida.reloj;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.configuracion.VersionVigente;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Estado técnico y semáforo de los relojes por la API del dashboard, contra la base real (decisiones del 13-11
 * y del 16-11-2026; las reglas una por una las prueba SemaforoRelojTest). Sin Docker se omite.
 * <ul>
 * <li>T1 Cada reloj trae lo que informó en su última sincronización del vínculo vigente: hora, batería, si está
 * puesto, conexión y versión de la app, y si tiene la configuración pendiente.</li>
 * <li>T2 El color y los motivos salen de esos datos: verde al día; rojo sin sincronizar hace 4 horas o con el
 * plan vencido; amarillo con batería baja, configuración pendiente, una alerta técnica abierta o una app más
 * antigua que la más nueva del programa; gris sin vincular.</li>
 * <li>T3 Exige sesión y USUARIOS:VER, como la lista de relojes.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosSemaforoRelojesTest {

	private static final String RUN = "15555555-6";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;
	@Autowired
	private VersionVigente versiones;

	private final Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
	private String token;
	private int pacientes;

	/** Base vacía y una coordinadora con sesión. */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "contacto",
				"registro_auditoria", "usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES ('COORDINADOR', 'cifrado', :hash, 'Camila', 'camila@muni.cl', :clave, true)""")
				.param("hash", hashRun.de(Run.de(RUN))).param("clave", encoder.encode("Clave-de-Camila1")).update();
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + RUN + "\", \"contrasena\": \"Clave-de-Camila1\"}"))
				.andReturn().getResponse().getContentAsString();
		token = JsonPath.read(respuesta, "$.token");
	}

	/** T1 y T2: un reloj al día trae sus datos y está verde, sin motivos. */
	@Test
	void unRelojAlDiaTraeSusDatosYEstaVerde() throws Exception {
		UUID reloj = relojVinculado("R-VERDE", null);
		UUID asignacion = asignacionDe(reloj);
		sincronizacion(asignacion, ahora.minus(Duration.ofMinutes(10)), 80, "1.2.0", vigenteDe(asignacion));

		estados().andExpect(status().isOk())
				.andExpect(jsonPath(campo(reloj, "semaforo")).value(contains("VERDE")))
				.andExpect(jsonPath(campo(reloj, "motivos")).value(contains(empty())))
				.andExpect(jsonPath(campo(reloj, "ultimaSincronizacion"))
						.value(contains(ahora.minus(Duration.ofMinutes(10)).toString())))
				.andExpect(jsonPath(campo(reloj, "nivelBateria")).value(contains(80)))
				.andExpect(jsonPath(campo(reloj, "relojPuesto")).value(contains(true)))
				.andExpect(jsonPath(campo(reloj, "tipoConexion")).value(contains("LTE")))
				.andExpect(jsonPath(campo(reloj, "versionApp")).value(contains("1.2.0")))
				.andExpect(jsonPath(campo(reloj, "configuracionPendiente")).value(contains(false)));
	}

	/** T2: sin vincular, gris; sin sincronizar hace 5 horas, rojo; con el plan vencido, rojo. */
	@Test
	void grisYRojo() throws Exception {
		UUID libre = relojLibre("R-LIBRE");
		UUID callado = relojVinculado("R-CALLADO", null);
		UUID asignacion = asignacionDe(callado);
		sincronizacion(asignacion, ahora.minus(Duration.ofHours(5)), 80, "1.2.0", vigenteDe(asignacion));
		UUID sinPlan = relojVinculado("R-SIN-PLAN", LocalDate.now(ZonaHoraria.CHILE).minusDays(1));
		UUID asignacionSinPlan = asignacionDe(sinPlan);
		sincronizacion(asignacionSinPlan, ahora.minus(Duration.ofMinutes(5)), 80, "1.2.0",
				vigenteDe(asignacionSinPlan));

		estados().andExpect(jsonPath(campo(libre, "semaforo")).value(contains("GRIS")))
				.andExpect(jsonPath(campo(libre, "motivos")).value(contains(contains("SIN_VINCULAR"))))
				.andExpect(jsonPath(campo(callado, "semaforo")).value(contains("ROJO")))
				.andExpect(jsonPath(campo(callado, "motivos")).value(contains(contains("SIN_SENAL"))))
				.andExpect(jsonPath(campo(sinPlan, "semaforo")).value(contains("ROJO")))
				.andExpect(jsonPath(campo(sinPlan, "motivos")).value(contains(contains("PLAN_VENCIDO"))));
	}

	/**
	 * T2: batería baja, configuración pendiente, una alerta técnica abierta y una app más antigua que la de otro
	 * reloj dan amarillo con todos sus motivos; la del reloj más nuevo no se marca.
	 */
	@Test
	void amarilloConTodosSusMotivos() throws Exception {
		UUID nuevo = relojVinculado("R-NUEVO", null);
		UUID asignacionNuevo = asignacionDe(nuevo);
		sincronizacion(asignacionNuevo, ahora.minus(Duration.ofMinutes(5)), 80, "1.10.0", vigenteDe(asignacionNuevo));
		UUID atrasado = relojVinculado("R-ATRASADO", null);
		UUID asignacion = asignacionDe(atrasado);
		sincronizacion(asignacion, ahora.minus(Duration.ofMinutes(5)), 10, "1.9.3", "configuracion-vieja");
		jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, origen, tipo_evento, severidad, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'SERVIDOR', 'BATERIA_BAJA', 'LEVE', :t)""")
				.param("a", asignacion).param("t", Timestamp.from(ahora.minus(Duration.ofMinutes(4)))).update();

		estados().andExpect(jsonPath(campo(nuevo, "semaforo")).value(contains("VERDE")))
				.andExpect(jsonPath(campo(atrasado, "semaforo")).value(contains("AMARILLO")))
				.andExpect(jsonPath(campo(atrasado, "configuracionPendiente")).value(contains(true)))
				.andExpect(jsonPath(campo(atrasado, "motivos")).value(contains(containsInAnyOrder("BATERIA_BAJA",
						"CONFIGURACION_PENDIENTE", "ALERTA_TECNICA_ABIERTA", "VERSION_ANTIGUA"))));
	}

	/** T3: sin sesión, 401. */
	@Test
	void sinSesionResponde401() throws Exception {
		mvc.perform(get("/api/relojes/estado")).andExpect(status().isUnauthorized());
	}

	/** @return la respuesta del estado técnico de los relojes, con la sesión de la coordinadora */
	private ResultActions estados() throws Exception {
		return mvc.perform(get("/api/relojes/estado").header("Authorization", "Bearer " + token));
	}

	/** @return la ruta JSON de ese campo del reloj (una lista de un elemento) */
	private static String campo(UUID reloj, String nombre) {
		return "$[?(@.idDispositivo == '" + reloj + "')]." + nombre;
	}

	/** @return un reloj operativo que nadie usa */
	private UUID relojLibre(String identificador) {
		return jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:i, 'OPERATIVO')
				RETURNING id_dispositivo""").param("i", identificador).query(UUID.class).single();
	}

	/** @return un reloj vinculado hace un día a un paciente nuevo, con ese vencimiento del plan (o sin él) */
	private UUID relojVinculado(String identificador, LocalDate vencimientoPlan) {
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado, fecha_vencimiento_plan)
				VALUES (:i, 'OPERATIVO', :v)
				RETURNING id_dispositivo""")
				.param("i", identificador).param("v", vencimientoPlan == null ? null : Date.valueOf(vencimientoPlan))
				.query(UUID.class).single();
		pacientes++;
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, :hash, 'Rosa', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("1234567" + pacientes + "-K", ColumnaCifrada.PACIENTE_RUT))
				.param("hash", "hash-semaforo-" + pacientes)
				.query(UUID.class).single();
		jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, :token)""")
				.param("r", reloj).param("p", paciente).param("inicio", Timestamp.from(ahora.minus(Duration.ofDays(1))))
				.param("token", "hash-token-semaforo-" + identificador).update();
		return reloj;
	}

	/** @return el vínculo vigente del reloj */
	private UUID asignacionDe(UUID reloj) {
		return jdbc.sql("SELECT id_asignacion FROM asignacion_reloj WHERE id_dispositivo = :r AND fecha_fin IS NULL")
				.param("r", reloj).query(UUID.class).single();
	}

	/** @return la versión vigente de la configuración del paciente de ese vínculo */
	private String vigenteDe(UUID asignacion) {
		UUID paciente = jdbc.sql("SELECT id_paciente FROM asignacion_reloj WHERE id_asignacion = :a")
				.param("a", asignacion).query(UUID.class).single();
		return versiones.de(paciente);
	}

	/** Una sincronización del vínculo, puesto y por LTE, con esa batería, versión de app y de configuración. */
	private void sincronizacion(UUID asignacion, Instant recepcion, int bateria, String versionApp,
			String configuracion) {
		jdbc.sql("""
				INSERT INTO sincronizacion (event_id, id_asignacion, hash_configuracion, fecha_hora_recepcion,
					nivel_bateria, reloj_puesto, tipo_conexion, version_app)
				VALUES (gen_random_uuid(), :a, :c, :t, :b, true, 'LTE', :v)""")
				.param("a", asignacion).param("c", configuracion).param("t", Timestamp.from(recepcion))
				.param("b", bateria).param("v", versionApp).update();
	}

}
