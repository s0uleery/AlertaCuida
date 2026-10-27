package cl.ucn.alertacuida.calendario;

import static org.hamcrest.Matchers.hasSize;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Calendario hábil de punta a punta: se lee de calendario.yml (con sus tildes) y lo ve
 * cualquier persona con sesión, sin un permiso en particular. Sin Docker se omite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class CalendarioTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	/** Base sin cuentas, salvo una del rol PERSONAL, el que tiene menos permisos. */
	@BeforeEach
	void preparar() {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "contacto",
				"registro_auditoria", "usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES ('PERSONAL', 'cifrado', :hash, 'Pers', 'pers@muni.cl', :clave, true)""")
				.param("hash", hashRun.de(Run.de("22.222.222-2")))
				.param("clave", encoder.encode("Clave-de-Pers1"))
				.update();
	}

	/** El rol PERSONAL ve el horario de lunes a viernes, con el viernes más corto, y los feriados ordenados. */
	@Test
	void cualquierPersonaConSesionVeElCalendario() throws Exception {
		mvc.perform(get("/api/calendario").header("Authorization", "Bearer " + token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.zonaHoraria").value("America/Santiago"))
				.andExpect(jsonPath("$.horarioHabil", hasSize(5)))
				.andExpect(jsonPath("$.horarioHabil[0].dia").value("LUNES"))
				.andExpect(jsonPath("$.horarioHabil[0].inicio").value("08:30"))
				.andExpect(jsonPath("$.horarioHabil[0].fin").value("17:30"))
				.andExpect(jsonPath("$.horarioHabil[4].dia").value("VIERNES"))
				.andExpect(jsonPath("$.horarioHabil[4].fin").value("16:30"))
				.andExpect(jsonPath("$.feriados[0].fecha").value("2026-01-01"))
				.andExpect(jsonPath("$.feriados[0].nombre").value("Año Nuevo"))
				.andExpect(jsonPath("$.feriados[2].nombre").value("Sábado Santo"));
	}

	/** Sin sesión, 401. */
	@Test
	void sinSesionNoSeVe() throws Exception {
		mvc.perform(get("/api/calendario")).andExpect(status().isUnauthorized());
	}

	/** Inicia sesión con la cuenta del rol PERSONAL y devuelve el token. */
	private String token() throws Exception {
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"22.222.222-2\", \"contrasena\": \"Clave-de-Pers1\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.token");
	}

}
