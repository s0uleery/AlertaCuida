package cl.ucn.alertacuida.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
 * Correo de activación de una cuenta del equipo, de punta a punta con un servidor de correo de prueba
 * (GreenMail): el administrador crea la cuenta, el enlace le llega solo a la persona (el dashboard ya no lo
 * muestra) y ella activa su cuenta con el enlace del correo. Sin Docker se omite.
 */
@SpringBootTest(properties = {"alertacuida.correo.canal=SMTP", "spring.mail.host=localhost",
	"spring.mail.port=3025", "alertacuida.correo.remitente=alertacuida@muni.cl"})
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosCorreoActivacionTest {

	/** Servidor SMTP de prueba en el puerto 3025: guarda los correos en memoria. */
	@RegisterExtension
	static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

	/** El enlace de activación en el cuerpo del correo; el grupo 1 es el token. */
	private static final Pattern ENLACE = Pattern.compile("http://localhost:5173/activar#([A-Za-z0-9_-]+)");

	private static final String CLAVE_NUEVA = "Una-clave-segura-1";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	private String administrador;

	/** Base vacía con un administrador activo. */
	@BeforeEach
	void preparar() throws Exception {
		BaseDePrueba.vaciar(jdbc);
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES ('ADMINISTRADOR', 'cifrado', :hash, 'Admin', 'admin@muni.cl', :clave, true)""")
				.param("hash", hashRun.de(Run.de("11.111.111-1")))
				.param("clave", encoder.encode("Clave-de-Admin1"))
				.update();
		administrador = JsonPath.read(login("11.111.111-1", "Clave-de-Admin1").andReturn().getResponse()
				.getContentAsString(), "$.token");
	}

	/**
	 * Al crear la cuenta, el correo le llega a la persona con su nombre y el enlace; el dashboard no recibe el
	 * enlace. La persona activa su cuenta con el enlace del correo y puede iniciar sesión.
	 */
	@Test
	void elEnlaceLlegaPorCorreoYSoloLaPersonaLoConoce() throws Exception {
		alta().andExpect(status().isCreated())
				.andExpect(jsonPath("$.correoEnviado").value(true))
				.andExpect(jsonPath("$.enlaceActivacion").value(nullValue()));

		MimeMessage[] recibidos = SMTP.getReceivedMessages();
		assertThat(recibidos).hasSize(1);
		assertThat(recibidos[0].getAllRecipients()[0].toString()).isEqualTo("ana@muni.cl");
		assertThat(recibidos[0].getSubject()).contains("Ana Pérez");
		String cuerpo = (String) recibidos[0].getContent();
		assertThat(cuerpo).contains("Ana Pérez");

		activar(token(cuerpo)).andExpect(status().isNoContent());
		login("12345678-5", CLAVE_NUEVA).andExpect(status().isOk());
	}

	/** Un enlace nuevo también sale por correo, y el del primer correo deja de servir. */
	@Test
	void unEnlaceNuevoSalePorCorreoYElAnteriorDejaDeServir() throws Exception {
		String idUsuario = JsonPath.read(alta().andReturn().getResponse().getContentAsString(), "$.idUsuario");

		mvc.perform(post("/api/personal/" + idUsuario + "/enlace-activacion")
				.header("Authorization", "Bearer " + administrador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correoEnviado").value(true))
				.andExpect(jsonPath("$.enlaceActivacion").value(nullValue()));

		MimeMessage[] recibidos = SMTP.getReceivedMessages();
		assertThat(recibidos).hasSize(2);
		activar(token((String) recibidos[0].getContent())).andExpect(status().isBadRequest());
		activar(token((String) recibidos[1].getContent())).andExpect(status().isNoContent());
	}

	/** El administrador crea la cuenta de Ana Pérez con rol PERSONAL. */
	private ResultActions alta() throws Exception {
		return mvc.perform(post("/api/personal").header("Authorization", "Bearer " + administrador)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"run": "12.345.678-5", "nombre": "Ana Pérez", "cargo": "Enfermera",
						"correo": "ana@muni.cl", "rol": "PERSONAL"}"""));
	}

	/** @return el token del enlace de activación que trae el cuerpo del correo */
	private static String token(String cuerpo) {
		Matcher enlace = ENLACE.matcher(cuerpo);
		assertThat(enlace.find()).as("el correo trae el enlace de activación").isTrue();
		return enlace.group(1);
	}

	/** Activa la cuenta con ese token (sin sesión). */
	private ResultActions activar(String token) throws Exception {
		return mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\", \"contrasena\": \"" + CLAVE_NUEVA + "\"}"));
	}

	/** Intenta iniciar sesión. */
	private ResultActions login(String run, String contrasena) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"" + contrasena + "\"}"));
	}

}
