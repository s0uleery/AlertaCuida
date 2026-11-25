package cl.ucn.alertacuida.reloj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Eliminar un reloj del inventario, según las decisiones del 24-11-2026. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Un reloj que nunca tuvo paciente se borra de verdad (204) y queda en la auditoría.</li>
 * <li>R2 Uno vinculado ahora no se borra: 409 {@code EN_USO} (primero hay que desvincularlo).</li>
 * <li>R3 Uno que tuvo paciente no se borra, para no perder sus lecturas ni alertas: 409 {@code CON_HISTORIAL}; se
 * puede pasar a RETIRADO.</li>
 * <li>R4 Exige el permiso USUARIOS:ELIMINAR (403 sin él) y responde 404 si el reloj no existe.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosEliminarRelojTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	private String administrador;
	private UUID idAdministrador;

	/** Base vacía con un administrador activo (tiene USUARIOS:ELIMINAR). */
	@BeforeEach
	void preparar() throws Exception {
		BaseDePrueba.vaciar(jdbc);
		idAdministrador = cuenta("ADMINISTRADOR", "11.111.111-1");
		administrador = sesion("11.111.111-1");
	}

	/** R1: un reloj sin historial se borra y queda auditado. */
	@Test
	void unRelojSinHistorialSeBorra() throws Exception {
		UUID reloj = reloj("R-NUEVO");

		eliminar(reloj, administrador).andExpect(status().isNoContent());

		assertThat(jdbc.sql("SELECT count(*) FROM smartwatch").query(Integer.class).single()).isZero();
		Integer auditado = jdbc.sql("""
				SELECT count(*) FROM registro_auditoria
				WHERE id_usuario = :u AND accion = 'ELIMINAR' AND entidad = 'SMARTWATCH' AND id_entidad = :r""")
				.param("u", idAdministrador).param("r", reloj.toString()).query(Integer.class).single();
		assertThat(auditado).isEqualTo(1);
	}

	/** R2: un reloj vinculado ahora responde EN_USO y no se borra. */
	@Test
	void unRelojEnUsoNoSeBorra() throws Exception {
		UUID reloj = reloj("R-EN-USO");
		asignacion(reloj, null);

		eliminar(reloj, administrador).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("EN_USO"));

		assertThat(jdbc.sql("SELECT count(*) FROM smartwatch").query(Integer.class).single()).isEqualTo(1);
	}

	/** R3: un reloj que tuvo paciente responde CON_HISTORIAL, no se borra, y se puede retirar. */
	@Test
	void unRelojConHistorialNoSeBorraPeroSePuedeRetirar() throws Exception {
		UUID reloj = reloj("R-USADO");
		asignacion(reloj, "now() - interval '1 day'");

		eliminar(reloj, administrador).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CON_HISTORIAL"));

		mvc.perform(put("/api/relojes/" + reloj)
				.header("Authorization", "Bearer " + administrador).contentType(MediaType.APPLICATION_JSON)
				.content("{\"identificadorReloj\": \"R-USADO\", \"estado\": \"RETIRADO\"}"))
				.andExpect(status().isNoContent());
		assertThat(jdbc.sql("SELECT estado FROM smartwatch").query(String.class).single()).isEqualTo("RETIRADO");
	}

	/** R4: sin el permiso, 403; un reloj que no existe, 404. */
	@Test
	void exigeElPermisoYQueElRelojExista() throws Exception {
		UUID reloj = reloj("R-PERMISO");
		cuenta("PERSONAL", "22.222.222-2");
		String personal = sesion("22.222.222-2");

		eliminar(reloj, personal).andExpect(status().isForbidden());
		eliminar(UUID.randomUUID(), administrador).andExpect(status().isNotFound());

		assertThat(jdbc.sql("SELECT count(*) FROM smartwatch").query(Integer.class).single()).isEqualTo(1);
	}

	// ---------- Ayudas ----------

	/** Pide eliminar el reloj con esa sesión. */
	private ResultActions eliminar(UUID reloj, String token) throws Exception {
		return mvc.perform(delete("/api/relojes/" + reloj).header("Authorization", "Bearer " + token));
	}

	/** @return un reloj operativo nuevo */
	private UUID reloj(String identificador) {
		return jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:i, 'OPERATIVO')
				RETURNING id_dispositivo""")
				.param("i", identificador).query(UUID.class).single();
	}

	/**
	 * Vincula el reloj a un paciente nuevo desde hace dos días.
	 *
	 * @param fin expresión SQL del fin del vínculo; null si sigue vigente
	 */
	private void asignacion(UUID reloj, String fin) {
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, fecha_ingreso, fecha_consentimiento)
				VALUES ('cifrado', :h, current_date - 10, current_date - 10) RETURNING id_paciente""")
				.param("h", "hash-" + UUID.randomUUID()).query(UUID.class).single();
		jdbc.sql("INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, fecha_fin, hash_token_reloj)"
				+ " VALUES (:r, :p, now() - interval '2 days', " + (fin == null ? "NULL" : fin) + ", :t)")
				.param("r", reloj).param("p", paciente).param("t", "token-" + UUID.randomUUID()).update();
	}

	/** @return una cuenta activa con ese rol y la contraseña "Clave-de-prueba1" */
	private UUID cuenta(String rol, String run) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, 'Persona', :correo, :clave, true) RETURNING id_usuario""")
				.param("rol", rol)
				.param("hash", hashRun.de(Run.de(run)))
				.param("correo", rol.toLowerCase() + "@muni.cl")
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
