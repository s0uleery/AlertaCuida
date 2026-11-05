package cl.ucn.alertacuida.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Alta y activación de cuentas del personal de punta a punta, y autorización por permiso
 * en los endpoints: HTTP, Spring Security, base de datos y auditoría. Sin Docker se omite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class CuentasPersonalTest {

	private static final String CLAVE_NUEVA = "mi frase secreta del prlac";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	/** Un administrador y un coordinador activos, con contraseña "Clave-de-" + nombre + "1". */
	@BeforeEach
	void crearUsuarios() {
		jdbc.sql("DELETE FROM registro_auditoria").update();
		jdbc.sql("DELETE FROM usuario").update();
		crear("11.111.111-1", "ADMINISTRADOR", "Admin", "admin@muni.cl");
		crear("22.222.222-2", "COORDINADOR", "Coord", "coord@muni.cl");
	}

	/** Sin token responde 401; el coordinador no tiene ADMINISTRACION:CREAR y recibe 403. */
	@Test
	void sinTokenResponde401YSinElPermisoResponde403() throws Exception {
		mvc.perform(post("/api/personal").contentType(MediaType.APPLICATION_JSON).content(cuenta("12.345.678-5")))
				.andExpect(status().isUnauthorized());
		// El coordinador tiene USUARIOS:CREAR (pacientes), pero no ADMINISTRACION:CREAR
		altaCon(token("22.222.222-2", "Clave-de-Coord1"), cuenta("12.345.678-5"))
				.andExpect(status().isForbidden());
	}

	/** El administrador crea la cuenta, la persona la activa con el enlace y puede iniciar sesión. */
	@Test
	void elAdministradorCreaLaCuentaLaPersonaLaActivaYPuedeIniciarSesion() throws Exception {
		ResultActions alta = altaCon(tokenAdmin(), cuenta("12.345.678-5"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.enlaceActivacion").value(startsWith(
						"http://localhost:5173/activar#")));
		String tokenEnlace = tokenDelEnlace(alta);

		activar(tokenEnlace, CLAVE_NUEVA).andExpect(status().isNoContent());

		login("12345678-5", CLAVE_NUEVA).andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.rol").value("PERSONAL"));
		Long altas = jdbc.sql("SELECT count(*) FROM registro_auditoria WHERE accion = 'CREAR' AND entidad = 'USUARIO'")
				.query(Long.class).single();
		assertThat(altas).isEqualTo(1);
		String rut = jdbc.sql("SELECT rut FROM usuario WHERE correo = 'ana@muni.cl'").query(String.class).single();
		assertThat(rut).startsWith("v1:").doesNotContain("12345678");
	}

	/** Antes de activarla, la cuenta no puede iniciar sesión. */
	@Test
	void antesDeActivarlaLaCuentaNoPuedeIniciarSesion() throws Exception {
		altaCon(tokenAdmin(), cuenta("12.345.678-5")).andExpect(status().isCreated());

		login("12345678-5", CLAVE_NUEVA).andExpect(status().isUnauthorized());
	}

	/** El enlace sirve una sola vez y una contraseña débil se rechaza. */
	@Test
	void elEnlaceSirveUnaSolaVezYLaContrasenaDebilSeRechaza() throws Exception {
		String tokenEnlace = tokenDelEnlace(altaCon(tokenAdmin(), cuenta("12.345.678-5")));

		activar(tokenEnlace, "corta").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("12")));
		activar(tokenEnlace, CLAVE_NUEVA).andExpect(status().isNoContent());
		activar(tokenEnlace, "otra frase secreta larga").andExpect(status().isBadRequest());
	}

	/** Un RUN repetido responde 409 y uno inválido, 400. */
	@Test
	void runRepetidoResponde409YRunInvalido400() throws Exception {
		String admin = tokenAdmin();
		altaCon(admin, cuenta("12.345.678-5")).andExpect(status().isCreated());

		altaCon(admin, cuenta("12345678-5")).andExpect(status().isConflict());
		altaCon(admin, cuenta("12.345.678-0")).andExpect(status().isBadRequest());
	}

	/** Un coordinador sin teléfono responde 400; con teléfono y opt-in se guarda cifrado y con la fecha. */
	@Test
	void elCoordinadorNecesitaTelefonoQueSeGuardaCifradoConSuOptIn() throws Exception {
		String admin = tokenAdmin();
		String sinTelefono = """
				{"run": "12.345.678-5", "nombre": "Rosa", "correo": "rosa@muni.cl", "rol": "COORDINADOR"}""";
		altaCon(admin, sinTelefono).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("WhatsApp")));

		altaCon(admin, """
				{"run": "12.345.678-5", "nombre": "Rosa", "correo": "rosa@muni.cl", "rol": "COORDINADOR",
				"telefono": "9 1234 5678", "aceptaWhatsapp": true}""").andExpect(status().isCreated());

		String telefono = jdbc.sql("SELECT telefono FROM usuario WHERE correo = 'rosa@muni.cl'").query(String.class)
				.single();
		assertThat(telefono).startsWith("v1:").doesNotContain("12345678");
		assertThat(jdbc.sql("SELECT fecha_opt_in IS NOT NULL FROM usuario WHERE correo = 'rosa@muni.cl'")
				.query(Boolean.class).single()).isTrue();
	}

	/** Un enlace nuevo reemplaza al anterior, y una cuenta activa no recibe enlaces (404). */
	@Test
	void unEnlaceNuevoReemplazaAlAnteriorYUnaCuentaActivaNoRecibeEnlaces() throws Exception {
		String admin = tokenAdmin();
		ResultActions alta = altaCon(admin, cuenta("12.345.678-5"));
		String primero = tokenDelEnlace(alta);
		String id = JsonPath.read(alta.andReturn().getResponse().getContentAsString(), "$.idUsuario");

		String segundo = tokenDelEnlace(nuevoEnlace(admin, id).andExpect(status().isOk()));

		activar(primero, CLAVE_NUEVA).andExpect(status().isBadRequest());
		activar(segundo, CLAVE_NUEVA).andExpect(status().isNoContent());
		nuevoEnlace(admin, id).andExpect(status().isNotFound());
	}

	/** Crea una cuenta del personal activa con contraseña "Clave-de-" + nombre + "1". */
	private void crear(String run, String rol, String nombre, String correo) {
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :clave, true)""")
				.param("rol", rol)
				.param("hash", hashRun.de(Run.de(run)))
				.param("nombre", nombre)
				.param("correo", correo)
				.param("clave", encoder.encode("Clave-de-" + nombre + "1"))
				.update();
	}

	/** Cuerpo del alta de una persona con rol PERSONAL. */
	private static String cuenta(String run) {
		return """
				{"run": "%s", "nombre": "Ana Pérez", "cargo": "Enfermera",
				"correo": "ana@muni.cl", "rol": "PERSONAL"}"""
				.formatted(run);
	}

	/** Pide el alta de una cuenta con ese token. */
	private ResultActions altaCon(String token, String cuerpo) throws Exception {
		return mvc.perform(post("/api/personal").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** Pide otro enlace de activación para la cuenta. */
	private ResultActions nuevoEnlace(String token, String idUsuario) throws Exception {
		return mvc.perform(post("/api/personal/" + idUsuario + "/enlace-activacion")
				.header("Authorization", "Bearer " + token));
	}

	/** Activa una cuenta con el token del enlace (sin sesión). */
	private ResultActions activar(String tokenEnlace, String contrasena) throws Exception {
		return mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + tokenEnlace + "\", \"contrasena\": \"" + contrasena + "\"}"));
	}

	/** Intenta iniciar sesión. */
	private ResultActions login(String run, String contrasena) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"" + contrasena + "\"}"));
	}

	/** @return el token de acceso del administrador de prueba */
	private String tokenAdmin() throws Exception {
		return token("11.111.111-1", "Clave-de-Admin1");
	}

	/** Inicia sesión y devuelve el token de acceso. */
	private String token(String run, String contrasena) throws Exception {
		return JsonPath.read(login(run, contrasena).andReturn().getResponse().getContentAsString(), "$.token");
	}

	/** Lo que va después de "#" en el enlace de activación. */
	private static String tokenDelEnlace(ResultActions respuesta) throws Exception {
		String enlace = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.enlaceActivacion");
		return enlace.substring(enlace.indexOf('#') + 1);
	}

}
