package cl.ucn.alertacuida.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pacientes (con sus condiciones), relojes y vínculos de punta a punta: HTTP, permisos, base de datos (incluidas
 * sus restricciones) y auditoría. Sin Docker se omite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class PacientesYRelojesTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	private String coordinador;

	/** Base vacía con un coordinador y una persona del rol PERSONAL. */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "registro_auditoria",
				"usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		crearUsuario("11.111.111-1", "COORDINADOR", "Coord");
		crearUsuario("22.222.222-2", "PERSONAL", "Pers");
		coordinador = token("11.111.111-1", "Clave-de-Coord1");
	}

	/** Sin token responde 401, y el rol PERSONAL no puede ver pacientes (403). */
	@Test
	void sinTokenResponde401YElRolPersonalNoVePacientes() throws Exception {
		mvc.perform(get("/api/pacientes")).andExpect(status().isUnauthorized());
		mvc.perform(conToken(get("/api/pacientes"), token("22.222.222-2", "Clave-de-Pers1")))
				.andExpect(status().isForbidden());
	}

	/** El alta aparece en la lista con su edad y sin reloj, y en la ficha sin historial. */
	@Test
	void registraYListaUnPacienteConSuEdad() throws Exception {
		String id = crearPaciente("12.345.678-5", "2026-01-10");

		mvc.perform(conToken(get("/api/pacientes"), coordinador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].paciente.datos.run").value("12345678-5"))
				.andExpect(jsonPath("$[0].edad").value(
						Period.between(LocalDate.of(1940, 3, 15), ZonaHoraria.hoy(Clock.systemUTC())).getYears()))
				.andExpect(jsonPath("$[0].relojActual").value(nullValue()));
		mvc.perform(conToken(get("/api/pacientes/" + id), coordinador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paciente.datos.nombres").value("María"))
				.andExpect(jsonPath("$.historial", hasSize(0)));
	}

	/** Un RUN repetido responde 409 y una fecha futura, 400. */
	@Test
	void runRepetidoResponde409YFechaFuturaResponde400() throws Exception {
		crearPaciente("12.345.678-5", "2026-01-10");

		conJson(post("/api/pacientes"), paciente("12345678-5", "2026-01-10")).andExpect(status().isConflict());
		conJson(post("/api/pacientes"), paciente("9.999.999-3", LocalDate.now().plusDays(2).toString()))
				.andExpect(status().isBadRequest());
	}

	/** Vincular entrega el token una vez; la base guarda solo su hash y las listas muestran el vínculo. */
	@Test
	void vincularEntregaElTokenUnaVezYLaBaseSoloGuardaSuHash() throws Exception {
		String maria = crearPaciente("12.345.678-5", "2026-01-10");
		String reloj = crearReloj("R-001");

		ResultActions vinculo = vincular(maria, reloj).andExpect(status().isCreated());
		String token = JsonPath.read(vinculo.andReturn().getResponse().getContentAsString(), "$.token");

		String guardado = jdbc.sql("SELECT hash_token_reloj FROM asignacion_reloj").query(String.class).single();
		assertThat(token).hasSizeGreaterThan(40);
		assertThat(guardado).isNotEqualTo(token).hasSize(64);
		mvc.perform(conToken(get("/api/pacientes"), coordinador))
				.andExpect(jsonPath("$[0].relojActual").value("R-001"));
		mvc.perform(conToken(get("/api/relojes"), coordinador))
				.andExpect(jsonPath("$[0].nombrePaciente").value("María Rojas"));
	}

	/** Un reloj no puede estar con dos pacientes, ni pasar a "en reparación" mientras se usa. */
	@Test
	void unRelojNoPuedeEstarConDosPacientesNiSalirDeServicioEnUso() throws Exception {
		String maria = crearPaciente("12.345.678-5", "2026-01-10");
		String pedro = crearPaciente("9.999.999-3", "2026-01-10");
		String reloj = crearReloj("R-001");
		vincular(maria, reloj);

		vincular(pedro, reloj)
				.andExpect(status().isConflict());
		conJson(put("/api/relojes/" + reloj), "{\"identificadorReloj\": \"R-001\", \"estado\": \"EN_REPARACION\"}")
				.andExpect(status().isConflict());
	}

	/** Sin consentimiento no se vincula (400). */
	@Test
	void sinConsentimientoNoSeVincula() throws Exception {
		String sinConsentimiento = crearPaciente("12.345.678-5", null);
		String reloj = crearReloj("R-001");

		vincular(sinConsentimiento, reloj).andExpect(status().isBadRequest());
	}

	/** El reemplazo conserva el historial y deja el reloj anterior en reparación. */
	@Test
	void reemplazarConservaElHistorialYDejaElAnteriorEnReparacion() throws Exception {
		String maria = crearPaciente("12.345.678-5", "2026-01-10");
		String reloj1 = crearReloj("R-001");
		String reloj2 = crearReloj("R-002");
		vincular(maria, reloj1);

		conJson(post("/api/pacientes/" + maria + "/reloj/reemplazo"),
				"{\"idDispositivo\": \"" + reloj2 + "\", \"estadoRelojAnterior\": \"EN_REPARACION\"}")
				.andExpect(status().isOk());

		mvc.perform(conToken(get("/api/pacientes/" + maria), coordinador))
				.andExpect(jsonPath("$.historial", hasSize(2)))
				.andExpect(jsonPath("$.historial[0].identificadorReloj").value("R-002"))
				.andExpect(jsonPath("$.historial[0].fin").value(nullValue()))
				.andExpect(jsonPath("$.historial[1].identificadorReloj").value("R-001"));
		String estado = jdbc.sql("SELECT estado FROM smartwatch WHERE identificador_reloj = 'R-001'")
				.query(String.class).single();
		assertThat(estado).isEqualTo("EN_REPARACION");
	}

	/** La baja cierra el vínculo, el reloj queda libre para otro y cada cambio queda en la auditoría. */
	@Test
	void laBajaCierraElVinculoYLiberaElReloj() throws Exception {
		String maria = crearPaciente("12.345.678-5", "2026-01-10");
		String pedro = crearPaciente("9.999.999-3", "2026-01-10");
		String reloj = crearReloj("R-001");
		vincular(maria, reloj);

		mvc.perform(conToken(post("/api/pacientes/" + maria + "/baja"), coordinador))
				.andExpect(status().isNoContent());

		mvc.perform(conToken(get("/api/pacientes"), coordinador)).andExpect(jsonPath("$", hasSize(1)));
		vincular(pedro, reloj)
				.andExpect(status().isCreated());
		mvc.perform(conToken(delete("/api/pacientes/" + pedro + "/reloj"), coordinador))
				.andExpect(status().isNoContent());
		Long cambios = jdbc.sql("SELECT count(*) FROM registro_auditoria WHERE entidad = 'ASIGNACION_RELOJ'")
				.query(Long.class).single();
		assertThat(cambios).isEqualTo(4);
	}

	/** Las condiciones se agregan, listan y quitan; una repetida responde 409 y cada cambio queda auditado. */
	@Test
	void agregaListaYQuitaCondiciones() throws Exception {
		String maria = crearPaciente("12.345.678-5", "2026-01-10");
		String ruta = "/api/pacientes/" + maria + "/condiciones";

		String epoc = id(conJson(post(ruta), "{\"tipo\": \"ENFERMEDAD\", \"descripcion\": \"EPOC\"}")
				.andExpect(status().isCreated()));
		conJson(post(ruta), "{\"tipo\": \"SITUACION\", \"descripcion\": \"Vive sola\"}")
				.andExpect(status().isCreated());
		conJson(post(ruta), "{\"tipo\": \"ENFERMEDAD\", \"descripcion\": \"epoc\"}").andExpect(status().isConflict());
		conJson(post(ruta), "{\"tipo\": \"OTRA\", \"descripcion\": \"x\"}").andExpect(status().isBadRequest());

		mvc.perform(conToken(get(ruta), coordinador))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].tipo").value("ENFERMEDAD"))
				.andExpect(jsonPath("$[1].descripcion").value("Vive sola"));
		mvc.perform(conToken(delete(ruta + "/" + epoc), coordinador)).andExpect(status().isNoContent());
		mvc.perform(conToken(delete(ruta + "/" + epoc), coordinador)).andExpect(status().isNotFound());
		mvc.perform(conToken(get(ruta), coordinador)).andExpect(jsonPath("$", hasSize(1)));
		Long cambios = jdbc.sql("SELECT count(*) FROM registro_auditoria WHERE entidad = 'CONDICION_PACIENTE'")
				.query(Long.class).single();
		assertThat(cambios).isEqualTo(3);
	}

	/** En la base, los datos personales y las condiciones están cifrados; por la API se leen en claro. */
	@Test
	void losDatosPersonalesQuedanCifradosEnLaBase() throws Exception {
		String maria = crearPaciente("12.345.678-5", "2026-01-10");
		conJson(post("/api/pacientes/" + maria + "/condiciones"),
				"{\"tipo\": \"ENFERMEDAD\", \"descripcion\": \"EPOC\"}").andExpect(status().isCreated());

		List<String> guardados = jdbc.sql("""
				SELECT unnest(ARRAY[p.rut, p.nombres, p.apellidos, p.fecha_nacimiento, c.descripcion])
				FROM paciente p JOIN condicion_paciente c USING (id_paciente)""")
				.query(String.class).list();
		assertThat(guardados).hasSize(5).allSatisfy(valor -> assertThat(valor).startsWith("v1:"))
				.noneMatch(valor -> valor.contains("12345678") || valor.contains("María") || valor.contains("Rojas")
						|| valor.contains("1940") || valor.contains("EPOC"));
		mvc.perform(conToken(get("/api/pacientes/" + maria), coordinador))
				.andExpect(jsonPath("$.paciente.datos.run").value("12345678-5"))
				.andExpect(jsonPath("$.paciente.datos.fechaNacimiento").value("1940-03-15"));
	}

	/** El rol PERSONAL no ve ni agrega condiciones (403). */
	@Test
	void elRolPersonalNoVeNiAgregaCondiciones() throws Exception {
		String ruta = "/api/pacientes/" + crearPaciente("12.345.678-5", "2026-01-10") + "/condiciones";
		String personal = token("22.222.222-2", "Clave-de-Pers1");

		mvc.perform(conToken(get(ruta), personal)).andExpect(status().isForbidden());
		mvc.perform(conToken(post(ruta), personal).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\": \"ENFERMEDAD\", \"descripcion\": \"EPOC\"}"))
				.andExpect(status().isForbidden());
	}

	/** Crea una cuenta del personal activa con contraseña "Clave-de-" + nombre + "1". */
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

	/**
	 * Paciente nacido el 15-03-1940.
	 *
	 * @param consentimiento fecha ISO, o null si no lo firmó
	 */
	private static String paciente(String run, String consentimiento) {
		return """
				{"run": "%s", "nombres": "María", "apellidos": "Rojas", "fechaNacimiento": "1940-03-15",
				"nivelDependencia": "SEVERA", "fechaConsentimiento": %s, "fechaIngreso": "2026-01-10"}"""
				.formatted(run, consentimiento == null ? "null" : "\"" + consentimiento + "\"");
	}

	/**
	 * Crea el paciente por la API como el coordinador.
	 *
	 * @return su identificador
	 */
	private String crearPaciente(String run, String consentimiento) throws Exception {
		return id(conJson(post("/api/pacientes"), paciente(run, consentimiento)).andExpect(status().isCreated()));
	}

	/**
	 * Registra un reloj operativo por la API como el coordinador.
	 *
	 * @return su identificador
	 */
	private String crearReloj(String identificador) throws Exception {
		return id(conJson(post("/api/relojes"), "{\"identificadorReloj\": \"" + identificador + "\"}")
				.andExpect(status().isCreated()));
	}

	/** El coordinador vincula el reloj al paciente. */
	private ResultActions vincular(String idPaciente, String idReloj) throws Exception {
		return conJson(post("/api/pacientes/" + idPaciente + "/reloj"), "{\"idDispositivo\": \"" + idReloj + "\"}");
	}

	/** Petición del coordinador con cuerpo JSON. */
	private ResultActions conJson(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
		return mvc.perform(conToken(peticion, coordinador).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** @return la petición con el token en la cabecera Authorization */
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

	/** @return el "id" de la respuesta de un alta */
	private static String id(ResultActions respuesta) throws Exception {
		return JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
