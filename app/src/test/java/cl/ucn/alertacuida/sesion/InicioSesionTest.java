package cl.ucn.alertacuida.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.sesion.LimpiarTokensVencidos;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
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

	private static final String COOKIE = "alertacuida_renovacion";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;
	@Autowired
	private LimpiarTokensVencidos limpiarTokens;

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

	/** El login deja el token de renovación en una cookie protegida, y en la base solo queda su hash. */
	@Test
	void elLoginDejaLaRenovacionEnUnaCookieProtegida() throws Exception {
		MockHttpServletResponse respuesta = login("12.345.678-5", "Clave-de-Ana1").andReturn().getResponse();

		String cabecera = respuesta.getHeader("Set-Cookie");
		assertThat(cabecera).startsWith(COOKIE + "=").contains("HttpOnly", "Secure", "SameSite=Strict",
				"Path=/api/auth");
		assertThat(respuesta.getContentAsString()).doesNotContain(respuesta.getCookie(COOKIE).getValue());
		Long conEseValor = jdbc.sql("SELECT count(*) FROM token_sesion WHERE hash_token = :valor")
				.param("valor", respuesta.getCookie(COOKIE).getValue()).query(Long.class).single();
		assertThat(conEseValor).isZero();
	}

	/** Con la cookie, sin token de acceso, se obtiene un token que sirve y una cookie distinta. */
	@Test
	void conLaCookieSeRenuevaLaSesion() throws Exception {
		Cookie cookie = login("12.345.678-5", "Clave-de-Ana1").andReturn().getResponse().getCookie(COOKIE);

		MockHttpServletResponse respuesta = mvc.perform(post("/api/auth/renovar").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.inactividadSegundos").value(900))
				.andExpect(jsonPath("$.usuario.nombre").value("Ana"))
				.andReturn().getResponse();

		assertThat(respuesta.getCookie(COOKIE).getValue()).isNotEqualTo(cookie.getValue());
		String token = JsonPath.read(respuesta.getContentAsString(), "$.token");
		mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
	}

	/** Sin cookie, la renovación responde 401 y ordena borrar la cookie. */
	@Test
	void sinCookieNoSeRenueva() throws Exception {
		mvc.perform(post("/api/auth/renovar"))
				.andExpect(status().isUnauthorized())
				.andExpect(cookie().maxAge(COOKIE, 0));
	}

	/** Reutilizar una cookie pasada la gracia termina todas las sesiones de la cuenta. */
	@Test
	void reutilizarUnaCookieTerminaLasSesiones() throws Exception {
		Cookie vieja = login("12.345.678-5", "Clave-de-Ana1").andReturn().getResponse().getCookie(COOKIE);
		Cookie nueva = mvc.perform(post("/api/auth/renovar").cookie(vieja)).andReturn().getResponse().getCookie(COOKIE);
		// Se simula que el reemplazo ocurrió hace rato, fuera de la gracia
		jdbc.sql("UPDATE token_sesion SET fecha_revocacion = now() - interval '1 minute' "
				+ "WHERE fecha_revocacion IS NOT NULL").update();

		mvc.perform(post("/api/auth/renovar").cookie(vieja)).andExpect(status().isUnauthorized());

		mvc.perform(post("/api/auth/renovar").cookie(nueva)).andExpect(status().isUnauthorized());
		Long registrados = jdbc.sql("""
				SELECT count(*) FROM registro_auditoria a JOIN usuario u USING (id_usuario)
				WHERE a.accion = 'TOKEN_REUTILIZADO' AND u.nombre_profesional = 'Ana' AND a.ip IS NOT NULL""")
				.query(Long.class).single();
		assertThat(registrados).isEqualTo(1);
	}

	/** La limpieza borra los tokens vencidos y conserva los vigentes. */
	@Test
	void laLimpiezaBorraSoloLosTokensVencidos() throws Exception {
		Cookie vencida = login("12.345.678-5", "Clave-de-Ana1").andReturn().getResponse().getCookie(COOKIE);
		jdbc.sql("""
				UPDATE token_sesion SET fecha_emision = now() - interval '2 days',
				fecha_expiracion = now() - interval '1 day'""").update();
		Cookie vigente = login("12.345.678-5", "Clave-de-Ana1").andReturn().getResponse().getCookie(COOKIE);

		limpiarTokens.limpiar();

		assertThat(jdbc.sql("SELECT count(*) FROM token_sesion").query(Long.class).single()).isEqualTo(1);
		mvc.perform(post("/api/auth/renovar").cookie(vencida)).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/renovar").cookie(vigente)).andExpect(status().isOk());
	}

	/** Tras cerrar sesión la cookie ya no sirve y se ordena borrarla. */
	@Test
	void trasSalirLaCookieNoSirve() throws Exception {
		Cookie cookie = login("12.345.678-5", "Clave-de-Ana1").andReturn().getResponse().getCookie(COOKIE);

		mvc.perform(post("/api/auth/salir").cookie(cookie))
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge(COOKIE, 0));

		mvc.perform(post("/api/auth/renovar").cookie(cookie)).andExpect(status().isUnauthorized());
	}

	/** Cerrar sesión sin cookie responde igual: no revela nada. */
	@Test
	void salirSinCookieRespondeIgual() throws Exception {
		mvc.perform(post("/api/auth/salir")).andExpect(status().isNoContent());
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
