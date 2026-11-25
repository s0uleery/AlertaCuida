package cl.ucn.alertacuida.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
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
 * Recuperar la contraseña por correo, de punta a punta con un servidor de correo de prueba (GreenMail). Cada
 * caso sale de las decisiones del 24-11-2026, no del código. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Desde el inicio de sesión, la persona escribe su RUN y, si su cuenta está activa, le llega un correo
 * con un enlace para elegir una contraseña nueva. Con ella entra; la anterior deja de servir.</li>
 * <li>R2 La respuesta es siempre la misma (202), exista o no la cuenta: no revela quién está registrado. Una
 * cuenta inexistente, pendiente de activar o desactivada no recibe correo.</li>
 * <li>R3 El enlace sirve una sola vez, vence a la hora, y pedir uno nuevo anula el anterior.</li>
 * <li>R4 Como máximo 3 correos de recuperación por cuenta en una hora, para que nadie inunde un buzón.</li>
 * <li>R5 Una contraseña que no cumple la política se rechaza (400) y el enlace sigue sirviendo.</li>
 * <li>R6 El cambio de contraseña queda en la auditoría.</li>
 * </ul>
 */
@SpringBootTest(properties = {"alertacuida.correo.canal=SMTP", "spring.mail.host=localhost",
	"spring.mail.port=3025", "alertacuida.correo.remitente=alertacuida@muni.cl"})
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosRecuperacionContrasenaTest {

	/** Servidor SMTP de prueba en el puerto 3025: guarda los correos en memoria. */
	@RegisterExtension
	static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

	/** El enlace de recuperación en el cuerpo del correo; el grupo 1 es el token. */
	private static final Pattern ENLACE = Pattern.compile("http://localhost:5173/restablecer#([A-Za-z0-9_-]+)");

	private static final String RUN = "12.345.678-5";
	private static final String CLAVE_VIEJA = "La-clave-vieja-1";
	private static final String CLAVE_NUEVA = "Una-clave-nueva-2";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	/** Base vacía. */
	@BeforeEach
	void preparar() {
		BaseDePrueba.vaciar(jdbc);
	}

	// ---------- R1 ----------

	/** R1: el correo llega a la persona con el enlace; con él elige otra contraseña y entra con ella. */
	@Test
	void laPersonaRecuperaSuContrasenaPorCorreo() throws Exception {
		cuenta(RUN, CLAVE_VIEJA, true, "ana@muni.cl");

		recuperar(RUN).andExpect(status().isAccepted());

		MimeMessage[] recibidos = SMTP.getReceivedMessages();
		assertThat(recibidos).hasSize(1);
		assertThat(recibidos[0].getAllRecipients()[0].toString()).isEqualTo("ana@muni.cl");
		String cuerpo = (String) recibidos[0].getContent();
		assertThat(cuerpo).contains("Ana Pérez");
		restablecer(token(cuerpo), CLAVE_NUEVA).andExpect(status().isNoContent());
		login(RUN, CLAVE_NUEVA).andExpect(status().isOk());
		login(RUN, CLAVE_VIEJA).andExpect(status().isUnauthorized());
	}

	// ---------- R2 ----------

	/** R2: un RUN sin cuenta responde igual y no envía nada. */
	@Test
	void unRunSinCuentaRespondeIgualYNoEnviaNada() throws Exception {
		recuperar(RUN).andExpect(status().isAccepted());

		assertThat(SMTP.getReceivedMessages()).isEmpty();
	}

	/** R2: una cuenta pendiente de activar no recupera: tiene su propio enlace de activación. */
	@Test
	void unaCuentaPendienteNoRecibeCorreo() throws Exception {
		cuenta(RUN, null, false, "ana@muni.cl");

		recuperar(RUN).andExpect(status().isAccepted());

		assertThat(SMTP.getReceivedMessages()).isEmpty();
	}

	/** R2: una cuenta desactivada no recupera: recuperar no debe servir para reactivarla. */
	@Test
	void unaCuentaDesactivadaNoRecibeCorreo() throws Exception {
		cuenta(RUN, CLAVE_VIEJA, false, "ana@muni.cl");

		recuperar(RUN).andExpect(status().isAccepted());

		assertThat(SMTP.getReceivedMessages()).isEmpty();
	}

	// ---------- R3 ----------

	/** R3: el enlace sirve una sola vez. */
	@Test
	void elEnlaceSirveUnaSolaVez() throws Exception {
		cuenta(RUN, CLAVE_VIEJA, true, "ana@muni.cl");
		recuperar(RUN);
		String token = token((String) SMTP.getReceivedMessages()[0].getContent());

		restablecer(token, CLAVE_NUEVA).andExpect(status().isNoContent());
		restablecer(token, "Otra-clave-mas-3").andExpect(status().isBadRequest());
	}

	/** R3: pedir otro enlace anula el anterior. */
	@Test
	void unEnlaceNuevoAnulaElAnterior() throws Exception {
		cuenta(RUN, CLAVE_VIEJA, true, "ana@muni.cl");
		recuperar(RUN);
		recuperar(RUN);

		MimeMessage[] recibidos = SMTP.getReceivedMessages();
		assertThat(recibidos).hasSize(2);
		restablecer(token((String) recibidos[0].getContent()), CLAVE_NUEVA).andExpect(status().isBadRequest());
		restablecer(token((String) recibidos[1].getContent()), CLAVE_NUEVA).andExpect(status().isNoContent());
	}

	/** R3: el enlace vence a la hora de emitido. */
	@Test
	void elEnlaceVenceALaHora() throws Exception {
		UUID ana = cuenta(RUN, CLAVE_VIEJA, true, "ana@muni.cl");
		recuperar(RUN);
		String token = token((String) SMTP.getReceivedMessages()[0].getContent());
		Long minutos = jdbc.sql("""
				SELECT round(extract(epoch FROM fecha_expiracion - fecha_emision) / 60) FROM token_accion
				WHERE id_usuario = :u AND tipo = 'RECUPERACION'""").param("u", ana).query(Long.class).single();
		assertThat(minutos).isEqualTo(60);

		jdbc.sql("""
				UPDATE token_accion SET fecha_emision = now() - interval '61 minutes',
					fecha_expiracion = now() - interval '1 minute'
				WHERE id_usuario = :u AND tipo = 'RECUPERACION'""").param("u", ana).update();

		restablecer(token, CLAVE_NUEVA).andExpect(status().isBadRequest());
	}

	/** R3: un enlace de recuperación no sirve para activar, ni uno de activación para recuperar. */
	@Test
	void losEnlacesNoSeMezclan() throws Exception {
		cuenta(RUN, CLAVE_VIEJA, true, "ana@muni.cl");
		recuperar(RUN);
		String token = token((String) SMTP.getReceivedMessages()[0].getContent());

		mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\", \"contrasena\": \"" + CLAVE_NUEVA + "\"}"))
				.andExpect(status().isBadRequest());
	}

	// ---------- R4 ----------

	/** R4: la cuarta solicitud en una hora responde igual, pero ya no envía correo. */
	@Test
	void comoMaximoTresCorreosPorHora() throws Exception {
		cuenta(RUN, CLAVE_VIEJA, true, "ana@muni.cl");

		for (int i = 0; i < 4; i++) {
			recuperar(RUN).andExpect(status().isAccepted());
		}

		assertThat(SMTP.getReceivedMessages()).hasSize(3);
	}

	// ---------- R5 ----------

	/** R5: una contraseña débil se rechaza y el mismo enlace sirve después con una buena. */
	@Test
	void unaContrasenaDebilSeRechazaYElEnlaceSigueSirviendo() throws Exception {
		cuenta(RUN, CLAVE_VIEJA, true, "ana@muni.cl");
		recuperar(RUN);
		String token = token((String) SMTP.getReceivedMessages()[0].getContent());

		restablecer(token, "corta").andExpect(status().isBadRequest());
		restablecer(token, "12345678-5").andExpect(status().isBadRequest());
		restablecer(token, CLAVE_NUEVA).andExpect(status().isNoContent());
	}

	// ---------- R6 ----------

	/** R6: el cambio de contraseña queda auditado como modificación de la cuenta. */
	@Test
	void elCambioQuedaEnLaAuditoria() throws Exception {
		UUID ana = cuenta(RUN, CLAVE_VIEJA, true, "ana@muni.cl");
		recuperar(RUN);
		jdbc.sql("DELETE FROM registro_auditoria").update();

		restablecer(token((String) SMTP.getReceivedMessages()[0].getContent()), CLAVE_NUEVA);

		Integer cambios = jdbc.sql("""
				SELECT count(*) FROM registro_auditoria
				WHERE id_usuario = :u AND accion = 'MODIFICAR' AND entidad = 'USUARIO' AND id_entidad = :id""")
				.param("u", ana).param("id", ana.toString()).query(Integer.class).single();
		assertThat(cambios).isEqualTo(1);
	}

	// ---------- Ayudas ----------

	/**
	 * Crea la cuenta de Ana Pérez con rol PERSONAL.
	 *
	 * @param clave  su contraseña; null si está pendiente de activar
	 * @param activa si puede entrar
	 * @return el id de la cuenta
	 */
	private UUID cuenta(String run, String clave, boolean activa, String correo) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES ('PERSONAL', 'cifrado', :hash, 'Ana Pérez', :correo, :clave, :activa) RETURNING id_usuario""")
				.param("hash", hashRun.de(Run.de(run)))
				.param("correo", correo)
				.param("clave", clave == null ? null : encoder.encode(clave))
				.param("activa", activa)
				.query(UUID.class).single();
	}

	/** @return el token del enlace de recuperación que trae el cuerpo del correo */
	private static String token(String cuerpo) {
		Matcher enlace = ENLACE.matcher(cuerpo);
		assertThat(enlace.find()).as("el correo trae el enlace de recuperación").isTrue();
		return enlace.group(1);
	}

	/** Pide recuperar la contraseña de ese RUN (sin sesión). */
	private ResultActions recuperar(String run) throws Exception {
		return mvc.perform(post("/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\"}"));
	}

	/** Elige la contraseña nueva con el token del correo (sin sesión). */
	private ResultActions restablecer(String token, String contrasena) throws Exception {
		return mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\", \"contrasena\": \"" + contrasena + "\"}"));
	}

	/** Intenta iniciar sesión. */
	private ResultActions login(String run, String contrasena) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"" + contrasena + "\"}"));
	}

}
