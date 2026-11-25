package cl.ucn.alertacuida.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
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
 * "Mi cuenta": cada persona del equipo enciende o apaga su propio WhatsApp (decisión del 24-11-2026). El correo
 * le sigue llegando siempre. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Cualquier cuenta del equipo, sin permisos especiales, ve sus datos (nombre, correo, teléfono y si recibe
 * WhatsApp) y cambia su teléfono y su WhatsApp.</li>
 * <li>R2 Encender guarda el opt-in con la hora; apagar lo quita y deja el teléfono.</li>
 * <li>R3 Sin teléfono no se enciende WhatsApp (400), y un coordinador no puede quedar sin teléfono (400).</li>
 * <li>R4 Solo cambia la cuenta propia (la de la sesión), y queda en la auditoría sin el número.</li>
 * <li>R5 Un coordinador que apagó su WhatsApp ya no lo recibe en el aviso a la oficina (solo el correo).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosMiCuentaTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	private UUID ana;
	private String sesionAna;

	/** Base vacía con Ana, del rol PERSONAL (sin permisos de administración), sin teléfono. */
	@BeforeEach
	void preparar() throws Exception {
		BaseDePrueba.vaciar(jdbc);
		ana = cuenta("PERSONAL", "12.345.678-5", "ana@muni.cl");
		sesionAna = sesion("12.345.678-5");
	}

	/** R1 y R2: Ana ve sus datos, enciende su WhatsApp con un teléfono y después lo apaga. */
	@Test
	void encenderYApagarElWhatsappPropio() throws Exception {
		miCuenta(sesionAna).andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Persona 12.345.678-5"))
				.andExpect(jsonPath("$.correo").value("ana@muni.cl"))
				.andExpect(jsonPath("$.telefono").value(nullValue()))
				.andExpect(jsonPath("$.whatsapp").value(false));

		whatsapp(sesionAna, "9 1234 5678", true).andExpect(status().isNoContent());

		miCuenta(sesionAna).andExpect(jsonPath("$.telefono").value("+56912345678"))
				.andExpect(jsonPath("$.whatsapp").value(true));
		assertThat(optIn(ana)).isTrue();

		whatsapp(sesionAna, "+56912345678", false).andExpect(status().isNoContent());

		miCuenta(sesionAna).andExpect(jsonPath("$.telefono").value("+56912345678"))
				.andExpect(jsonPath("$.whatsapp").value(false));
		assertThat(optIn(ana)).isFalse();
	}

	/** R3: sin teléfono no se enciende; un coordinador no queda sin teléfono, pero sí puede apagar su WhatsApp. */
	@Test
	void sinTelefonoNoHayWhatsappYElCoordinadorNoQuedaSinTelefono() throws Exception {
		whatsapp(sesionAna, "", true).andExpect(status().isBadRequest());

		cuenta("COORDINADOR", "11.111.111-1", "coord@muni.cl");
		String coordinador = sesion("11.111.111-1");
		whatsapp(coordinador, "9 1111 1111", true).andExpect(status().isNoContent());
		whatsapp(coordinador, "", false).andExpect(status().isBadRequest());
		whatsapp(coordinador, "9 1111 1111", false).andExpect(status().isNoContent());
	}

	/** R4: el cambio es siempre de la cuenta de la sesión, y la auditoría no guarda el número. */
	@Test
	void soloCambiaLaCuentaPropiaYQuedaAuditado() throws Exception {
		UUID luis = cuenta("PERSONAL", "22.222.222-2", "luis@muni.cl");
		jdbc.sql("DELETE FROM registro_auditoria").update();

		whatsapp(sesionAna, "9 1234 5678", true).andExpect(status().isNoContent());

		assertThat(optIn(luis)).isFalse();
		String detalle = jdbc.sql("""
				SELECT detalle::text FROM registro_auditoria
				WHERE id_usuario = :u AND accion = 'MODIFICAR' AND entidad = 'USUARIO' AND id_entidad = :id""")
				.param("u", ana).param("id", ana.toString()).query(String.class).single();
		assertThat(detalle).doesNotContain("1234");
	}

	/** R5: el coordinador que apagó su WhatsApp no recibe WhatsApp en el aviso a la oficina. */
	@Test
	void elCoordinadorApagadoNoRecibeWhatsappDeLaOficina() throws Exception {
		UUID coordinador = cuenta("COORDINADOR", "11.111.111-1", "coord@muni.cl");
		String sesion = sesion("11.111.111-1");
		whatsapp(sesion, "9 1111 1111", true);
		whatsapp(sesion, "9 1111 1111", false);

		// La misma condición que usa el aviso a la oficina para incluir el WhatsApp de un coordinador
		Integer conWhatsapp = jdbc.sql("""
				SELECT count(*) FROM usuario WHERE id_usuario = :u AND telefono IS NOT NULL
					AND fecha_opt_in IS NOT NULL AND fecha_revocacion_opt_in IS NULL""")
				.param("u", coordinador).query(Integer.class).single();
		assertThat(conWhatsapp).isZero();
	}

	// ---------- Ayudas ----------

	/** @return si la cuenta tiene un opt-in de WhatsApp vigente */
	private boolean optIn(UUID cuenta) {
		return jdbc.sql("""
				SELECT fecha_opt_in IS NOT NULL AND fecha_revocacion_opt_in IS NULL FROM usuario
				WHERE id_usuario = :u""").param("u", cuenta).query(Boolean.class).single();
	}

	/** Pide los datos de la cuenta propia. */
	private ResultActions miCuenta(String token) throws Exception {
		return mvc.perform(get("/api/mi-cuenta").header("Authorization", "Bearer " + token));
	}

	/** Cambia el teléfono y el WhatsApp de la cuenta propia. */
	private ResultActions whatsapp(String token, String telefono, boolean acepta) throws Exception {
		return mvc.perform(put("/api/mi-cuenta/whatsapp").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"telefono\": \"" + telefono + "\", \"aceptaWhatsapp\": " + acepta + "}"));
	}

	/**
	 * Una cuenta activa con ese rol, contraseña "Clave-de-prueba1" y nombre "Persona " + RUN. Un coordinador
	 * nace con teléfono y sin WhatsApp, como exige el alta.
	 */
	private UUID cuenta(String rol, String run, String correo) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :clave, true) RETURNING id_usuario""")
				.param("rol", rol)
				.param("hash", hashRun.de(Run.de(run)))
				.param("nombre", "Persona " + run)
				.param("correo", correo)
				.param("clave", encoder.encode("Clave-de-prueba1"))
				.query(UUID.class).single();
	}

	/** @return el token de acceso de esa cuenta */
	private String sesion(String run) throws Exception {
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"Clave-de-prueba1\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(cuerpo, "$.token");
	}

}
