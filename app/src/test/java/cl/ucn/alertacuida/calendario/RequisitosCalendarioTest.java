package cl.ucn.alertacuida.calendario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * Jornada hábil editable y simulador de canales del Calendario (decisiones del 25-11-2026). Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Quien tiene ADMINISTRACION:EDITAR cambia la jornada: queda guardada, la consulta la muestra, rige al tiro
 * (sin reiniciar) y queda en la auditoría. Sin el permiso, 403.</li>
 * <li>R2 Un día que termina antes de empezar, una hora mal escrita o una jornada sin días hábiles se rechaza con 400
 * y la jornada no cambia.</li>
 * <li>R3 El simulador dice si el momento es hábil, si una leve espera al día hábil, si se avisa a la oficina PRLAC
 * y a qué contactos del usuario avisaría el reloj, con las reglas que el sistema aplica hoy.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosCalendarioTest {

	/** Un lunes y un sábado de noviembre de 2026 que no son feriados. */
	private static final String LUNES = "2026-11-23";
	private static final String SABADO = "2026-11-28";

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
	@Autowired
	private CalendarioHabil calendario;

	private Map<DiaSemana, TramoHabil> original;
	private String administrador;

	/** Base vacía, con un administrador; guarda la jornada para devolverla al terminar. */
	@BeforeEach
	void preparar() throws Exception {
		BaseDePrueba.vaciar(jdbc);
		original = calendario.horario();
		cuenta("ADMINISTRADOR", "11.111.111-1");
		administrador = sesion("11.111.111-1");
	}

	/** El calendario es compartido por todas las pruebas: se deja como estaba. */
	@AfterEach
	void restaurar() {
		calendario.cambiarHorario(original);
		jdbc.sql("DELETE FROM horario_habil").update();
		original.forEach((dia, tramo) -> jdbc.sql("INSERT INTO horario_habil VALUES (:d, :i, :f)")
				.param("d", dia.name()).param("i", tramo.inicio()).param("f", tramo.fin()).update());
	}

	// ---------- R1 ----------

	/** R1: la jornada nueva queda guardada, se muestra, rige al tiro y queda auditada. */
	@Test
	void laJornadaNuevaRigeAlTiro() throws Exception {
		simular(LUNES, "09:00", "LEVE", null).andExpect(jsonPath("$.habil").value(true));

		guardar(administrador, """
				{"horarioHabil": [{"dia": "LUNES", "inicio": "10:00", "fin": "12:00"},
				                  {"dia": "MARTES", "inicio": "08:30", "fin": "17:30"}]}""")
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/calendario").header("Authorization", "Bearer " + administrador))
				.andExpect(jsonPath("$.horarioHabil", hasSize(2)))
				.andExpect(jsonPath("$.horarioHabil[0].inicio").value("10:00"));
		simular(LUNES, "09:00", "LEVE", null).andExpect(jsonPath("$.habil").value(false))
				.andExpect(jsonPath("$.esperaDiaHabil").value(true));
		assertThat(jdbc.sql("SELECT count(*) FROM horario_habil").query(Integer.class).single()).isEqualTo(2);
		assertThat(jdbc.sql("""
				SELECT count(*) FROM registro_auditoria WHERE accion = 'MODIFICAR' AND entidad = 'CALENDARIO'""")
				.query(Integer.class).single()).isEqualTo(1);
	}

	/** R1: sin ADMINISTRACION:EDITAR, 403. */
	@Test
	void sinPermisoNoSeCambia() throws Exception {
		cuenta("PERSONAL", "22.222.222-2");

		guardar(sesion("22.222.222-2"), """
				{"horarioHabil": [{"dia": "LUNES", "inicio": "10:00", "fin": "12:00"}]}""")
				.andExpect(status().isForbidden());
	}

	// ---------- R2 ----------

	/** R2: lo mal escrito se rechaza y la jornada sigue igual. */
	@Test
	void unaJornadaMalaSeRechaza() throws Exception {
		guardar(administrador, """
				{"horarioHabil": [{"dia": "LUNES", "inicio": "17:00", "fin": "09:00"}]}""")
				.andExpect(status().isBadRequest());
		guardar(administrador, """
				{"horarioHabil": [{"dia": "LUNES", "inicio": "8 y media", "fin": "17:30"}]}""")
				.andExpect(status().isBadRequest());
		guardar(administrador, "{\"horarioHabil\": []}").andExpect(status().isBadRequest());

		assertThat(calendario.horario()).isEqualTo(original);
	}

	// ---------- R3 ----------

	/** R3: una grave fuera de horario avisa a los contactos pero no a la oficina; una leve espera al día hábil. */
	@Test
	void elSimuladorAplicaLasReglasDeHoy() throws Exception {
		UUID paciente = pacienteConContacto("Hija");

		simular(SABADO, "15:00", "GRAVE", paciente).andExpect(status().isOk())
				.andExpect(jsonPath("$.habil").value(false))
				.andExpect(jsonPath("$.esperaDiaHabil").value(false))
				.andExpect(jsonPath("$.avisoOficina").value(false))
				.andExpect(jsonPath("$.contactos", hasSize(1)))
				.andExpect(jsonPath("$.contactos[0].nombre").value("Hija"))
				.andExpect(jsonPath("$.contactos[0].canales[0]").value("CORREO"));
		simular(SABADO, "15:00", "LEVE", paciente).andExpect(jsonPath("$.esperaDiaHabil").value(true))
				.andExpect(jsonPath("$.contactos", hasSize(0)));
		simular(LUNES, "10:00", "LEVE", paciente).andExpect(jsonPath("$.habil").value(true))
				.andExpect(jsonPath("$.avisoOficina").value(true))
				.andExpect(jsonPath("$.contactos", hasSize(1)));
	}

	/** R3: una fecha o una hora mal escritas responden 400. */
	@Test
	void elSimuladorRechazaFechasMalas() throws Exception {
		simular("25-11-2026", "10:00", "LEVE", null).andExpect(status().isBadRequest());
		simular(LUNES, "diez", "LEVE", null).andExpect(status().isBadRequest());
	}

	// ---------- Ayudas ----------

	/** Cambia la jornada con esa sesión. */
	private ResultActions guardar(String token, String cuerpo) throws Exception {
		return mvc.perform(put("/api/calendario/horario").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** Pide la simulación como administrador. */
	private ResultActions simular(String fecha, String hora, String gravedad, UUID paciente) throws Exception {
		var pedido = get("/api/calendario/simulacion").header("Authorization", "Bearer " + administrador)
				.param("fecha", fecha).param("hora", hora).param("gravedad", gravedad);
		if (paciente != null) {
			pedido = pedido.param("idPaciente", paciente.toString());
		}
		return mvc.perform(pedido);
	}

	/** @return un paciente con un contacto con correo, disponible todo el día */
	private UUID pacienteConContacto(String nombre) {
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, fecha_ingreso, fecha_consentimiento)
				VALUES ('cifrado', 'hash-simulacion', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.query(UUID.class).single();
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, correo, correo_hash) VALUES (:nombre, :correo, 'hash-c-simulacion')
				RETURNING id_contacto""")
				.param("nombre", cifrado.cifrar(nombre, ColumnaCifrada.CONTACTO_NOMBRE))
				.param("correo", cifrado.cifrar("hija@correo.cl", ColumnaCifrada.CONTACTO_CORREO))
				.query(UUID.class).single();
		BaseDePrueba.vincular(jdbc, paciente, persona, "TERCERO_SIGNIFICATIVO", 1);
		return paciente;
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
