package cl.ucn.alertacuida.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Auditoría de punta a punta: qué lecturas y cambios quedan registrados, quién puede consultar el
 * registro y que no guarde datos personales. Sin Docker se omite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class AuditoriaTest {

	private static final String RUN_PACIENTE = "12.345.678-5";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	private String administrador;
	private String coordinador;
	private String idPaciente;

	/** Base con un administrador, un coordinador y un paciente; el registro parte vacío en cada prueba. */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "registro_auditoria",
				"usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		crearUsuario("11.111.111-1", "ADMINISTRADOR", "Admin");
		crearUsuario("22.222.222-2", "COORDINADOR", "Coord");
		administrador = token("11.111.111-1", "Clave-de-Admin1");
		coordinador = token("22.222.222-2", "Clave-de-Coord1");
		String respuesta = mvc.perform(conToken(post("/api/pacientes"), coordinador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"run": "%s", "nombres": "María", "apellidos": "Rojas", "fechaNacimiento": "1940-03-15",
						"nivelDependencia": "SEVERA", "fechaConsentimiento": "2026-01-10",
						"fechaIngreso": "2026-01-10"}"""
						.formatted(RUN_PACIENTE)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		idPaciente = JsonPath.read(respuesta, "$.id");
		jdbc.sql("DELETE FROM registro_auditoria").update();
	}

	/** Cada parte de la ficha deja su propia lectura, con quién la hizo. */
	@Test
	void cadaParteDeLaFichaDejaSuLectura() throws Exception {
		String base = "/api/pacientes/" + idPaciente;
		for (String ruta : List.of(base, base + "/condiciones", base + "/contactos", base + "/perfil-clinico",
				base + "/reloj/configuracion")) {
			mvc.perform(conToken(get(ruta), coordinador)).andExpect(status().isOk());
		}

		assertThat(jdbc.sql("""
				SELECT entidad FROM registro_auditoria a JOIN usuario u USING (id_usuario)
				WHERE accion = 'LEER' AND id_entidad = :id AND u.nombre_profesional = 'Coord' AND ip IS NOT NULL""")
				.param("id", idPaciente).query(String.class).list())
				.containsExactlyInAnyOrder("PACIENTE", "CONDICION_PACIENTE", "CONTACTO_PACIENTE", "CONFIGURACION",
						"ASIGNACION_RELOJ");
	}

	/** Las listas con datos de pacientes dejan una sola fila por consulta. */
	@Test
	void lasListasDejanUnaSolaLectura() throws Exception {
		mvc.perform(conToken(get("/api/pacientes"), coordinador)).andExpect(status().isOk());
		mvc.perform(conToken(get("/api/relojes"), coordinador)).andExpect(status().isOk());

		assertThat(jdbc.sql("SELECT entidad FROM registro_auditoria WHERE accion = 'LEER' AND id_entidad = 'LISTA'")
				.query(String.class).list()).containsExactlyInAnyOrder("PACIENTE", "SMARTWATCH");
	}

	/** Una consulta fallida no entregó datos, así que no deja lectura. */
	@Test
	void unaConsultaFallidaNoDejaLectura() throws Exception {
		mvc.perform(conToken(get("/api/pacientes/" + UUID.randomUUID()), coordinador))
				.andExpect(status().isNotFound());

		assertThat(cantidad("LEER")).isZero();
	}

	/** Sin permiso no se lee, y tampoco queda lectura. */
	@Test
	void sinPermisoNoQuedaLectura() throws Exception {
		crearUsuario("33.333.333-3", "PERSONAL", "Pers");
		String personal = token("33.333.333-3", "Clave-de-Pers1");

		mvc.perform(conToken(get("/api/pacientes"), personal)).andExpect(status().isForbidden());

		assertThat(cantidad("LEER")).isZero();
	}

	/** Solo el administrador consulta el registro. */
	@Test
	void soloElAdministradorConsultaElRegistro() throws Exception {
		mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
		mvc.perform(conToken(get("/api/auditoria"), coordinador)).andExpect(status().isForbidden());
		mvc.perform(conToken(get("/api/auditoria"), administrador)).andExpect(status().isOk());
	}

	/** Por defecto el registro trae los cambios, y las lecturas solo si se piden. */
	@Test
	void lasLecturasSoloVienenSiSePiden() throws Exception {
		mvc.perform(conToken(get("/api/pacientes"), coordinador)).andExpect(status().isOk());
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"22.222.222-2\", \"contrasena\": \"Clave-de-Coord1\"}"));

		mvc.perform(conToken(get("/api/auditoria"), administrador))
				.andExpect(jsonPath("$[*].accion", hasItem("LOGIN")))
				.andExpect(jsonPath("$[*].accion", not(hasItem("LEER"))));
		mvc.perform(conToken(get("/api/auditoria").param("incluirLecturas", "true"), administrador))
				.andExpect(jsonPath("$[*].accion", hasItem("LEER")));
	}

	/** Cada fila dice quién hizo qué, del más nuevo al más antiguo. */
	@Test
	void cadaFilaDiceQuienHizoQue() throws Exception {
		mvc.perform(conToken(get("/api/pacientes/" + idPaciente), coordinador)).andExpect(status().isOk());

		mvc.perform(conToken(get("/api/auditoria").param("incluirLecturas", "true"), administrador))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].accion").value("LEER"))
				.andExpect(jsonPath("$[0].entidad").value("PACIENTE"))
				.andExpect(jsonPath("$[0].idEntidad").value(idPaciente))
				.andExpect(jsonPath("$[0].usuario.nombre").value("Coord"));
	}

	/** Un login fallido con un RUN que no existe se muestra sin autor y sin el RUN escrito. */
	@Test
	void unLoginFallidoNoGuardaElRunEscrito() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"44.444.444-4\", \"contrasena\": \"x\"}"));

		String respuesta = mvc.perform(conToken(get("/api/auditoria"), administrador))
				.andExpect(jsonPath("$[0].accion").value("LOGIN_FALLIDO"))
				.andExpect(jsonPath("$[0].usuario").isEmpty())
				.andReturn().getResponse().getContentAsString();
		assertThat(respuesta).doesNotContain("44.444.444", "44444444");
	}

	/** Tras leer y cambiar datos del paciente, el registro no contiene ninguno de sus datos personales. */
	@Test
	void elRegistroNoGuardaDatosPersonales() throws Exception {
		mvc.perform(conToken(get("/api/pacientes/" + idPaciente), coordinador)).andExpect(status().isOk());
		mvc.perform(conToken(post("/api/pacientes/" + idPaciente + "/condiciones"), coordinador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\": \"ENFERMEDAD\", \"descripcion\": \"Diabetes\"}"))
				.andExpect(status().isCreated());

		String registro = String.join(" ", jdbc.sql("""
				SELECT concat_ws(' ', accion, entidad, id_entidad, detalle::text, ip) FROM registro_auditoria""")
				.query(String.class).list());
		assertThat(registro).contains("CONDICION_PACIENTE")
				.doesNotContain("María", "Rojas", "12.345.678", "12345678", "Diabetes");
	}

	/** @return cuántas filas del registro tienen esa acción */
	private Long cantidad(String accion) {
		return jdbc.sql("SELECT count(*) FROM registro_auditoria WHERE accion = :accion")
				.param("accion", accion).query(Long.class).single();
	}

	/** Crea una cuenta activa con contraseña "Clave-de-" + nombre + "1". */
	private void crearUsuario(String run, String rol, String nombre) {
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :clave, true)""")
				.param("rol", rol)
				.param("hash", hashRun.de(Run.de(run)))
				.param("nombre", nombre)
				.param("correo", nombre.toLowerCase() + "@muni.cl")
				.param("clave", encoder.encode("Clave-de-" + nombre + "1"))
				.update();
	}

	/** Agrega el token de acceso a la petición. */
	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header("Authorization", "Bearer " + token);
	}

	/** Inicia sesión y devuelve el token de acceso. */
	private String token(String run, String contrasena) throws Exception {
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"" + contrasena + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.token");
	}

}
