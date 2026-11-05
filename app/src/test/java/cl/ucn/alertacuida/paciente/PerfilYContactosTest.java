package cl.ucn.alertacuida.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
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
 * Perfil clínico y contactos de un paciente de punta a punta: HTTP, permisos, base de datos (incluidos
 * sus índices únicos), cifrado y auditoría. Sin Docker se omite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class PerfilYContactosTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	private String coordinador;
	private String maria;

	/** Base vacía con un coordinador, una persona del rol PERSONAL y una paciente. */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "contacto",
				"registro_auditoria", "usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		crearUsuario("11.111.111-1", "COORDINADOR", "Coord");
		crearUsuario("22.222.222-2", "PERSONAL", "Pers");
		coordinador = token("11.111.111-1", "Clave-de-Coord1");
		maria = crearPaciente("12.345.678-5");
	}

	/** Sin ajustes rigen los valores base de application.properties. */
	@Test
	void sinAjustesRigenLosValoresBase() throws Exception {
		mvc.perform(conToken(get(perfil()), coordinador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ajustes.modulos.caidas").value(true))
				.andExpect(jsonPath("$.ajustes.modulos.tendencia").value(true))
				.andExpect(jsonPath("$.minutosEspera").value(5))
				.andExpect(jsonPath("$.fc.propio").value(false))
				.andExpect(jsonPath("$.fc.umbral.graveBajo").value(40))
				.andExpect(jsonPath("$.spo2.umbral.normalMin").value(92));
	}

	/** Cambiar ajustes y umbrales agrega filas al historial, con el motivo cifrado y su auditoría. */
	@Test
	void ajustesYUmbralesQuedanEnElHistorialConAuditoria() throws Exception {
		conJson(put(perfil() + "/ajustes"), """
				{"modulos": {"caidas": true, "fc": true, "spo2": true, "inactividad": false, "tendencia": false,
				"zonaSegura": false, "botonAyuda": true}, "gpsActivo": true, "modoConservador": false,
				"minutosEspera": 8, "motivo": "Sale a caminar sola"}""")
				.andExpect(status().isNoContent());
		conJson(put(perfil() + "/umbrales/SPO2"), "{\"graveBajo\": 85, \"normalMin\": 90}")
				.andExpect(status().isNoContent());
		conJson(put(perfil() + "/umbrales/FC"),
				"{\"graveBajo\": 60, \"normalMin\": 50, \"normalMax\": 110, \"graveAlto\": 130}")
				.andExpect(status().isBadRequest());

		mvc.perform(conToken(get(perfil()), coordinador))
				.andExpect(jsonPath("$.ajustes.gpsActivo").value(true))
				.andExpect(jsonPath("$.ajustes.motivo").value("Sale a caminar sola"))
				.andExpect(jsonPath("$.minutosEspera").value(8))
				.andExpect(jsonPath("$.spo2.propio").value(true))
				.andExpect(jsonPath("$.spo2.umbral.graveBajo").value(85));
		mvc.perform(conToken(delete(perfil() + "/umbrales/SPO2"), coordinador)).andExpect(status().isNoContent());
		mvc.perform(conToken(get(perfil()), coordinador))
				.andExpect(jsonPath("$.spo2.propio").value(false))
				.andExpect(jsonPath("$.spo2.umbral.graveBajo").value(88));

		assertThat(jdbc.sql("SELECT motivo_cambio FROM configuracion").query(String.class).single()).startsWith("v1:");
		assertThat(jdbc.sql("SELECT count(*) FROM umbral WHERE variable = 'SPO2'").query(Integer.class).single())
				.isEqualTo(2);
		assertThat(jdbc.sql("SELECT entidad FROM registro_auditoria WHERE accion = 'CREAR' AND entidad <> 'PACIENTE'")
				.query(String.class).list()).containsExactlyInAnyOrder("CONFIGURACION", "UMBRAL", "UMBRAL");
	}

	/** La zona se guarda con coordenadas cifradas; activarla exige GPS y queda en el perfil. */
	@Test
	void laZonaSeguraSeGuardaCifradaYSeActivaConGps() throws Exception {
		String conZonaSinGps = """
				{"modulos": {"caidas": true, "fc": true, "spo2": true, "inactividad": true, "tendencia": true,
				"zonaSegura": true, "botonAyuda": true}, "gpsActivo": false, "modoConservador": false}""";
		conJson(put(perfil() + "/zona-segura"), "{\"latitud\": -23.6509, \"longitud\": -70.3975, \"radioMetros\": 20}")
				.andExpect(status().isBadRequest());
		conJson(put(perfil() + "/zona-segura"), "{\"latitud\": -23.6509, \"longitud\": -70.3975, \"radioMetros\": 200}")
				.andExpect(status().isNoContent());
		conJson(put(perfil() + "/ajustes"), conZonaSinGps).andExpect(status().isBadRequest());
		conJson(put(perfil() + "/ajustes"), conZonaSinGps.replace("\"gpsActivo\": false", "\"gpsActivo\": true"))
				.andExpect(status().isNoContent());

		mvc.perform(conToken(get(perfil()), coordinador))
				.andExpect(jsonPath("$.ajustes.modulos.zonaSegura").value(true))
				.andExpect(jsonPath("$.ajustes.modulos.botonAyuda").value(true))
				.andExpect(jsonPath("$.zonaSegura.zona.latitud").value(-23.6509))
				.andExpect(jsonPath("$.zonaSegura.zona.radioMetros").value(200))
				.andExpect(jsonPath("$.zonaSegura.zona.severidad").value("MEDIA"));
		assertThat(jdbc.sql("SELECT latitud FROM zona_segura").query(String.class).single()).startsWith("v1:")
				.doesNotContain("23.65");
	}

	/** Los contactos se guardan cifrados y con hash; la lista muestra canales y horas sin cobertura. */
	@Test
	void losContactosQuedanCifradosYLaListaMuestraLaCobertura() throws Exception {
		String ana = agregar(maria, contacto("Ana Rojas", "9 1234 5678", null, "FAMILIA", null, null));
		agregar(maria, contacto("Luis Pérez", null, "Luis@Mail.cl", "VECINO", "08:00", "20:00"));

		mvc.perform(conToken(get(contactos(maria)), coordinador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contactos", hasSize(2)))
				.andExpect(jsonPath("$.contactos[0].whatsapp").value("+56912345678"))
				.andExpect(jsonPath("$.contactos[0].canales", hasSize(0)))
				.andExpect(jsonPath("$.contactos[1].correo").value("luis@mail.cl"))
				.andExpect(jsonPath("$.contactos[1].horaInicio").value("08:00"))
				.andExpect(jsonPath("$.sinCobertura[0].inicio").value("20:00"))
				.andExpect(jsonPath("$.sinCobertura[0].fin").value("08:00"));
		conJson(put(contactos(maria) + "/" + ana + "/opt-in"), "{\"aceptado\": true}")
				.andExpect(status().isNoContent());
		mvc.perform(conToken(get(contactos(maria)), coordinador))
				.andExpect(jsonPath("$.contactos[0].canales", contains("WHATSAPP")))
				.andExpect(jsonPath("$.sinCobertura", hasSize(0)));

		String nombre = jdbc.sql("SELECT nombre FROM contacto WHERE whatsapp_hash IS NOT NULL")
				.query(String.class).single();
		String hash = jdbc.sql("SELECT whatsapp_hash FROM contacto WHERE whatsapp_hash IS NOT NULL")
				.query(String.class).single();
		assertThat(nombre).startsWith("v1:");
		assertThat(hash).hasSize(64);
	}

	/** El SAMU no se acepta, la misma persona no se repite y se reutiliza en otro paciente. */
	@Test
	void rechazaAlSamuYReutilizaLaPersonaEnOtroPaciente() throws Exception {
		conJson(post(contactos(maria)), contacto("SAMU", "131", null, "INSTITUCION", null, null))
				.andExpect(status().isBadRequest());
		agregar(maria, contacto("Seguridad Ciudadana", null, "seguridad@muni.cl", "SEGURIDAD_CIUDADANA", null, null));
		conJson(post(contactos(maria)), contacto("Seguridad", null, "seguridad@muni.cl", "SEGURIDAD_CIUDADANA", null,
				null)).andExpect(status().isConflict());
		String pedro = crearPaciente("9.999.999-3");
		agregar(pedro, contacto("Seguridad Ciudadana", null, "seguridad@muni.cl", "SEGURIDAD_CIUDADANA", null, null));

		assertThat(jdbc.sql("SELECT count(*) FROM contacto").query(Integer.class).single()).isEqualTo(1);
		mvc.perform(conToken(get(contactos(pedro)), coordinador))
				.andExpect(jsonPath("$.contactos[0].otrosPacientes").value(1));
	}

	/** Reordenar y desactivar respetan el índice único de prioridad sin dejar huecos. */
	@Test
	void reordenarYDesactivarRespetanLaPrioridadUnica() throws Exception {
		String ana = agregar(maria, contacto("Ana", "912345678", null, "FAMILIA", null, null));
		String luis = agregar(maria, contacto("Luis", null, "luis@mail.cl", "VECINO", null, null));

		conJson(put(contactos(maria) + "/orden"), "{\"orden\": [\"" + luis + "\", \"" + ana + "\"]}")
				.andExpect(status().isNoContent());
		mvc.perform(conToken(get(contactos(maria)), coordinador))
				.andExpect(jsonPath("$.contactos[0].id").value(luis))
				.andExpect(jsonPath("$.contactos[0].prioridad").value(1));
		mvc.perform(conToken(post(contactos(maria) + "/" + luis + "/desactivar"), coordinador))
				.andExpect(status().isNoContent());
		mvc.perform(conToken(get(contactos(maria)), coordinador))
				.andExpect(jsonPath("$.contactos[0].id").value(ana))
				.andExpect(jsonPath("$.contactos[0].prioridad").value(1))
				.andExpect(jsonPath("$.contactos[1].activo").value(false));
		mvc.perform(conToken(post(contactos(maria) + "/" + luis + "/reactivar"), coordinador))
				.andExpect(status().isNoContent());
		mvc.perform(conToken(get(contactos(maria)), coordinador))
				.andExpect(jsonPath("$.contactos[1].id").value(luis))
				.andExpect(jsonPath("$.contactos[1].prioridad").value(2));
	}

	/** El rol PERSONAL no ve contactos ni perfil clínico; el catálogo de tipos lo ve el coordinador. */
	@Test
	void elRolPersonalNoVeContactosNiPerfil() throws Exception {
		String personal = token("22.222.222-2", "Clave-de-Pers1");

		mvc.perform(conToken(get(contactos(maria)), personal)).andExpect(status().isForbidden());
		mvc.perform(conToken(get(perfil()), personal)).andExpect(status().isForbidden());
		mvc.perform(conToken(get("/api/tipos-contacto"), coordinador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].codigo", hasItem("FAMILIA")));
	}

	/** @return la ruta del perfil clínico de María */
	private String perfil() {
		return "/api/pacientes/" + maria + "/perfil-clinico";
	}

	/** @return la ruta de los contactos del paciente */
	private static String contactos(String idPaciente) {
		return "/api/pacientes/" + idPaciente + "/contactos";
	}

	/** @return el cuerpo JSON de un contacto; las horas "HH:mm" o null para todo el día */
	private static String contacto(String nombre, String whatsapp, String correo, String tipo, String inicio,
			String fin) {
		return """
				{"nombre": "%s", "whatsapp": %s, "correo": %s, "tipo": "%s", "cuidadorPrincipal": false,
				"horaInicio": %s, "horaFin": %s}""".formatted(nombre, texto(whatsapp), texto(correo), tipo,
				texto(inicio), texto(fin));
	}

	/** @return el valor entre comillas, o null */
	private static String texto(String valor) {
		return valor == null ? "null" : "\"" + valor + "\"";
	}

	/** Agrega el contacto como el coordinador y devuelve su identificador. */
	private String agregar(String idPaciente, String cuerpo) throws Exception {
		return id(conJson(post(contactos(idPaciente)), cuerpo).andExpect(status().isCreated()));
	}

	/** Crea una cuenta activa con contraseña "Clave-de-{nombre}1". */
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

	/** Crea una paciente con consentimiento por la API y devuelve su identificador. */
	private String crearPaciente(String run) throws Exception {
		return id(conJson(post("/api/pacientes"), """
				{"run": "%s", "nombres": "María", "apellidos": "Rojas", "fechaNacimiento": "1940-03-15",
				"nivelDependencia": "SEVERA", "fechaConsentimiento": "2026-01-10", "fechaIngreso": "2026-01-10"}"""
				.formatted(run)).andExpect(status().isCreated()));
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
