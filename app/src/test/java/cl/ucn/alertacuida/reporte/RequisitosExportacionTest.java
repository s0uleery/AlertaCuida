package cl.ucn.alertacuida.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
 * Exportación de los reportes a Excel y PDF desde Reportería (decisión del 25-11-2026). Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 El reporte de un usuario y la gestión operativa se descargan como .xlsx y como .pdf, con su nombre de
 * archivo, y cada descarga queda en la auditoría como EXPORTAR REPORTE.</li>
 * <li>R2 Exige REPORTES:EXPORTAR: sin él, 403.</li>
 * <li>R3 Un período al revés responde 400; un formato o un usuario que no existe, 404.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosExportacionTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;
	@Autowired
	private CifradoCampos cifrado;

	private String administrador;
	private UUID paciente;

	/** Base vacía, un administrador (tiene REPORTES:EXPORTAR) y un usuario del programa. */
	@BeforeEach
	void preparar() throws Exception {
		BaseDePrueba.vaciar(jdbc);
		cuenta("ADMINISTRADOR", "11.111.111-1");
		administrador = sesion("11.111.111-1");
		paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, nombres, apellidos, fecha_ingreso)
				VALUES (:rut, 'hash-exportar', :nombres, :apellidos, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.param("nombres", cifrado.cifrar("María", ColumnaCifrada.PACIENTE_NOMBRES))
				.param("apellidos", cifrado.cifrar("Soto", ColumnaCifrada.PACIENTE_APELLIDOS))
				.query(UUID.class).single();
	}

	/** R1: el reporte semanal del usuario sale en Excel (un .zip: empieza con "PK") y en PDF. */
	@Test
	void elReporteDelUsuarioSeExporta() throws Exception {
		byte[] excel = exportar("/api/pacientes/" + paciente + "/reportes/semanal.xlsx", administrador)
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("reporte-semanal.xlsx")))
				.andReturn().getResponse().getContentAsByteArray();
		byte[] pdf = exportar("/api/pacientes/" + paciente + "/reportes/diario.pdf", administrador)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

		assertThat(new String(excel, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
		assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
		assertThat(exportaciones()).isEqualTo(2);
	}

	/** R1: la gestión operativa del período sale en los dos formatos. */
	@Test
	void laGestionSeExporta() throws Exception {
		exportar("/api/reportes/gestion.xlsx?desde=2026-11-01&hasta=2026-11-25", administrador)
				.andExpect(status().isOk());
		exportar("/api/reportes/gestion.pdf?desde=2026-11-01&hasta=2026-11-25", administrador)
				.andExpect(status().isOk());

		assertThat(exportaciones()).isEqualTo(2);
	}

	/** R2: sin REPORTES:EXPORTAR, 403. */
	@Test
	void exigeElPermiso() throws Exception {
		cuenta("PERSONAL", "22.222.222-2");

		exportar("/api/reportes/gestion.pdf?desde=2026-11-01&hasta=2026-11-25", sesion("22.222.222-2"))
				.andExpect(status().isForbidden());
	}

	/** R3: período al revés 400; formato, período o usuario inexistente 404. */
	@Test
	void loQueNoCalzaSeRechaza() throws Exception {
		exportar("/api/reportes/gestion.pdf?desde=2026-11-25&hasta=2026-11-01", administrador)
				.andExpect(status().isBadRequest());
		exportar("/api/reportes/gestion.docx?desde=2026-11-01&hasta=2026-11-25", administrador)
				.andExpect(status().isNotFound());
		exportar("/api/pacientes/" + paciente + "/reportes/mensual.pdf", administrador)
				.andExpect(status().isNotFound());
		exportar("/api/pacientes/" + UUID.randomUUID() + "/reportes/diario.pdf", administrador)
				.andExpect(status().isNotFound());
	}

	// ---------- Ayudas ----------

	/** Pide una exportación con esa sesión. */
	private ResultActions exportar(String ruta, String token) throws Exception {
		return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
	}

	/** @return cuántas exportaciones quedaron en la auditoría */
	private int exportaciones() {
		return jdbc.sql("SELECT count(*) FROM registro_auditoria WHERE accion = 'EXPORTAR' AND entidad = 'REPORTE'")
				.query(Integer.class).single();
	}

	/** @return una cuenta activa con ese rol y la contraseña "Clave-de-prueba1" */
	private UUID cuenta(String rol, String run) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, 'Persona', :correo, :clave, true) RETURNING id_usuario""")
				.param("rol", rol).param("hash", hashRun.de(Run.de(run)))
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
