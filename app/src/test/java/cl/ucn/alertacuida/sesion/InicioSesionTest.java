package cl.ucn.alertacuida.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Inicio de sesión de punta a punta: HTTP, Spring Security, base de datos y auditoría. Sin Docker se omite. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class InicioSesionTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	/**
	 * Base con dos cuentas: Ana (PERSONAL, activa) y Luis (COORDINADOR, inactiva). Contraseña
	 * "Clave-de-" + nombre + "1".
	 */
	@BeforeEach
	void crearUsuarios() {
		jdbc.sql("DELETE FROM registro_auditoria").update();
		jdbc.sql("DELETE FROM usuario").update();
		crear("12.345.678-5", "PERSONAL", "Ana", "ana@muni.cl", true);
		crear("11.111.111-1", "COORDINADOR", "Luis", "luis@muni.cl", false);
	}

	/** Con RUN y contraseña entra, recibe los permisos de su rol y /yo responde quién es. */
	@Test
	void conRunYContrasenaEntraYRecibeLosPermisosDeSuRol() throws Exception {
		String token = token(login("12.345.678-5", "Clave-de-Ana1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.rol").value("PERSONAL"))
				.andExpect(jsonPath("$.usuario.permisos").value(hasItem("ALERTAS:EDITAR")))
				.andExpect(jsonPath("$.usuario.permisos").value(not(hasItem("REPORTES:VER")))));

		mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana"));
	}

	/** El RUN sin puntos es el mismo RUN. */
	@Test
	void elRunSinPuntosEsElMismo() throws Exception {
		login("12345678-5", "Clave-de-Ana1").andExpect(status().isOk());
	}

	/** Contraseña incorrecta, RUN inexistente y cuenta inactiva dan el mismo error. */
	@Test
	void contrasenaIncorrectaRunInexistenteYCuentaInactivaDanElMismoError() throws Exception {
		rechazado(login("12.345.678-5", "otra"));
		rechazado(login("22.222.222-2", "x"));
		rechazado(login("11.111.111-1", "Clave-de-Luis1"));
	}

	/** Cada intento, correcto o fallido, queda en la auditoría. */
	@Test
	void cadaIntentoQuedaEnLaAuditoria() throws Exception {
		login("12.345.678-5", "otra");
		login("12.345.678-5", "Clave-de-Ana1");

		Long fallidos = jdbc.sql("SELECT count(*) FROM registro_auditoria WHERE accion = 'LOGIN_FALLIDO'")
				.query(Long.class).single();
		Long correctos = jdbc.sql("SELECT count(*) FROM registro_auditoria WHERE accion = 'LOGIN'")
				.query(Long.class).single();
		assertThat(fallidos).isEqualTo(1);
		assertThat(correctos).isEqualTo(1);
	}

	/** Tres fallos bloquean la cuenta. */
	@Test
	void tresFallosBloqueanLaCuenta() throws Exception {
		for (int i = 0; i < 3; i++) {
			login("12.345.678-5", "otra");
		}
		rechazado(login("12.345.678-5", "Clave-de-Ana1"));
	}

	/** Sin token, o con un token falso, no se entra. */
	@Test
	void sinTokenOConTokenFalsoNoSeEntra() throws Exception {
		mvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer falso")).andExpect(status().isUnauthorized());
	}

	/** Sin contraseña la petición es inválida (400). */
	@Test
	void faltaLaContrasena() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"run\": \"1-9\"}"))
				.andExpect(status().isBadRequest());
	}

	/** Crea una cuenta del personal con contraseña "Clave-de-" + nombre + "1". */
	private void crear(String run, String rol, String nombre, String correo, boolean activo) {
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :clave, :activo)""")
				.param("rol", rol)
				.param("hash", hashRun.de(Run.de(run)))
				.param("nombre", nombre)
				.param("correo", correo)
				.param("clave", encoder.encode("Clave-de-" + nombre + "1"))
				.param("activo", activo)
				.update();
	}

	/** Intenta iniciar sesión. */
	private ResultActions login(String run, String contrasena) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"" + contrasena + "\"}"));
	}

	/** Toda falla responde 401 con el mismo mensaje. */
	private static void rechazado(ResultActions respuesta) throws Exception {
		respuesta.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("RUN o contraseña incorrectos"));
	}

	/** @return el token de acceso de una respuesta de login */
	private static String token(ResultActions respuesta) throws Exception {
		return JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.token");
	}

}
