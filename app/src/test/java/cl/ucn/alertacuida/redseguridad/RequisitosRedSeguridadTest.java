package cl.ucn.alertacuida.redseguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Red de seguridad del servidor vista desde afuera. Los casos salen del cronograma (2.7: listar los posibles
 * eventos no alertados, sin avisar a nadie), del modelo de datos (los hallazgos se calculan, no se guardan) y
 * de las decisiones del 19-11-2026 (versión mínima), no del código. Las alertas las decide el reloj: esto
 * solo sirve para auditarlo. Sin Docker se omite.
 *
 * <ul>
 * <li>H1 Hallazgo = una lectura de FC o SpO2 más allá del límite grave (estricto: el límite mismo no cuenta).</li>
 * <li>H2 No es hallazgo si hay una alerta del reloj de FC, SpO2, FC+SpO2 o tendencia a 30 minutos o menos,
 * aunque el paciente la haya cancelado. Una caída o un simulacro no cuentan.</li>
 * <li>H3 Se compara con el umbral vigente al medir: el propio del paciente o, si no tiene, el base.</li>
 * <li>H4 Con el módulo de esa variable apagado al medir, no es hallazgo.</li>
 * <li>H5 Rango de fechas de Chile, por defecto los últimos 7 días; desde no puede ser posterior a hasta ni el
 * rango pasar de 31 días (400).</li>
 * <li>H6 Cada hallazgo trae el paciente, la variable, el valor, la hora y el límite superado; el más reciente
 * primero. No se guarda nada ni se avisa a nadie.</li>
 * <li>H7 Exige REPORTES:VER y la lectura queda auditada.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosRedSeguridadTest {

	private static final String RUN_COORDINADORA = "11.111.111-1";
	private static final String RUN_PERSONAL = "22.222.222-2";

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

	private final Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
	private UUID paciente;
	private UUID asignacion;
	private String token;

	/** Base vacía; una paciente con su reloj desde hace un mes y una coordinadora con sesión. */
	@BeforeEach
	void preparar() throws Exception {
		BaseDePrueba.vaciar(jdbc);
		paciente = paciente("María", "Soto Rojas");
		asignacion = reloj(paciente, "R-RED-1");
		usuario(RUN_COORDINADORA, "COORDINADOR", "Camila");
		token = token(RUN_COORDINADORA, "Camila");
	}

	// ---------- H1: lecturas más allá del límite grave ----------

	/** H1: con los umbrales base, FC 135 y 38 y SpO2 85 son hallazgos; FC 130, FC 40, SpO2 88 y 95, no. */
	@Test
	void soloLasLecturasMasAllaDelLimiteGrave() throws Exception {
		lectura("FC", 135, horasAtras(1));
		lectura("FC", 38, horasAtras(2));
		lectura("SPO2", 85, horasAtras(3));
		lectura("FC", 130, horasAtras(4));
		lectura("FC", 40, horasAtras(5));
		lectura("SPO2", 88, horasAtras(6));
		lectura("SPO2", 95, horasAtras(7));

		hallazgos(null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].valor").value(contains(135.0, 38.0, 85.0)));
	}

	// ---------- H2: cruce con las alertas del reloj ----------

	/** H2: una alerta del reloj a 30 minutos o menos tapa la lectura, también si fue cancelada. */
	@Test
	void unaAlertaCercanaDelRelojLaTapa() throws Exception {
		lectura("FC", 135, horasAtras(1));
		alertaDelReloj("FC", horasAtras(1).plus(Duration.ofMinutes(30)), false, false);
		lectura("SPO2", 85, horasAtras(3));
		alertaDelReloj("FC_SPO2", horasAtras(3).minus(Duration.ofMinutes(10)), false, false);
		lectura("FC", 136, horasAtras(5));
		alertaDelReloj("TENDENCIA", horasAtras(5), false, false);
		lectura("SPO2", 84, horasAtras(7));
		alertaDelReloj("SPO2", horasAtras(7).plus(Duration.ofMinutes(5)), true, false);

		hallazgos(null, null).andExpect(jsonPath("$", hasSize(0)));
	}

	/** H2: una alerta a más de 30 minutos, una caída o un simulacro no tapan la lectura. */
	@Test
	void alertasQueNoCuentan() throws Exception {
		lectura("FC", 135, horasAtras(1));
		alertaDelReloj("FC", horasAtras(1).plus(Duration.ofMinutes(31)), false, false);
		lectura("FC", 137, horasAtras(3));
		alertaDelReloj("CAIDA", horasAtras(3), false, false);
		lectura("FC", 139, horasAtras(5));
		alertaDelReloj("FC", horasAtras(5), false, true);

		hallazgos(null, null).andExpect(jsonPath("$[*].valor").value(contains(135.0, 137.0, 139.0)));
	}

	/** H2: la alerta de otro reloj no tapa la lectura de este. */
	@Test
	void laAlertaDeOtroRelojNoCuenta() throws Exception {
		UUID otro = reloj(paciente("Otra", "Persona"), "R-RED-2");
		lectura("FC", 135, horasAtras(1));
		jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', 'FC', 'GRAVE', :t, :t)""")
				.param("a", otro).param("t", Timestamp.from(horasAtras(1))).update();

		hallazgos(null, null).andExpect(jsonPath("$", hasSize(1)));
	}

	// ---------- H3: umbral vigente al medir ----------

	/**
	 * H3: con un umbral propio de FC (grave alto 140) vigente al medir, FC 135 no es hallazgo; si el umbral
	 * propio empezó después de la lectura, rige el base y sí lo es.
	 */
	@Test
	void seComparaConElUmbralVigenteAlMedir() throws Exception {
		umbralFc(horasAtras(10), 35, 45, 120, 140);
		lectura("FC", 135, horasAtras(9));
		lectura("FC", 145, horasAtras(8));
		UUID otra = paciente("Rosa", "Pérez");
		asignacion = reloj(otra, "R-RED-3");
		lectura("FC", 135, horasAtras(2));
		umbralFc(horasAtras(1), 35, 45, 120, 140, otra);

		hallazgos(null, null).andExpect(jsonPath("$[*].valor").value(contains(135.0, 145.0)))
				.andExpect(jsonPath("$[0].limiteGrave").value(130))
				.andExpect(jsonPath("$[1].limiteGrave").value(140));
	}

	/** H3: una fila de umbral vacía vuelve al umbral base. */
	@Test
	void unUmbralVacioVuelveAlBase() throws Exception {
		umbralFc(horasAtras(10), 35, 45, 120, 140);
		jdbc.sql("INSERT INTO umbral (id_paciente, variable, vigente_desde) VALUES (:p, 'FC', :d)")
				.param("p", paciente).param("d", Timestamp.from(horasAtras(5))).update();
		lectura("FC", 135, horasAtras(1));

		hallazgos(null, null).andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].limiteGrave").value(130));
	}

	// ---------- H4: módulo apagado ----------

	/** H4: con FC apagada al medir, FC 135 no es hallazgo; la SpO2, encendida, sí. */
	@Test
	void conElModuloApagadoNoCuenta() throws Exception {
		jdbc.sql("INSERT INTO configuracion (id_paciente, vigente_desde, fc_activa) VALUES (:p, :d, false)")
				.param("p", paciente).param("d", Timestamp.from(horasAtras(10))).update();
		lectura("FC", 135, horasAtras(2));
		lectura("SPO2", 85, horasAtras(3));

		hallazgos(null, null).andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].variable").value("SPO2"));
	}

	// ---------- H5: rango de fechas ----------

	/** H5: por defecto, los últimos 7 días: una lectura de hace 6 días aparece; una de hace 8, no. */
	@Test
	void porDefectoLosUltimosSieteDias() throws Exception {
		lectura("FC", 135, ahora.minus(Duration.ofDays(6)));
		lectura("FC", 136, ahora.minus(Duration.ofDays(8)));

		hallazgos(null, null).andExpect(jsonPath("$[*].valor").value(contains(135.0)));
	}

	/** H5: desde y hasta son días de Chile, los dos incluidos. */
	@Test
	void desdeYHastaSonDiasDeChile() throws Exception {
		LocalDate haceDiezDias = LocalDate.now(ZonaHoraria.CHILE).minusDays(10);
		Instant mediodia = haceDiezDias.atTime(12, 0).atZone(ZonaHoraria.CHILE).toInstant();
		lectura("FC", 135, mediodia);
		lectura("FC", 136, mediodia.plus(Duration.ofDays(1)));

		hallazgos(haceDiezDias, haceDiezDias).andExpect(jsonPath("$[*].valor").value(contains(135.0)));
	}

	/** H5: desde posterior a hasta, o más de 31 días, se rechazan. */
	@Test
	void unRangoInvalidoSeRechaza() throws Exception {
		LocalDate hoy = LocalDate.now(ZonaHoraria.CHILE);
		hallazgos(hoy, hoy.minusDays(1)).andExpect(status().isBadRequest());
		hallazgos(hoy.minusDays(31), hoy).andExpect(status().isBadRequest());
		hallazgos(hoy.minusDays(30), hoy).andExpect(status().isOk());
	}

	// ---------- H6: formato ----------

	/** H6: cada hallazgo dice de quién es, qué se midió, cuándo y qué límite superó; no se guarda nada. */
	@Test
	void cadaHallazgoTraeSusDatos() throws Exception {
		Instant medicion = horasAtras(1);
		lectura("SPO2", 85, medicion);
		int alertasAntes = jdbc.sql("SELECT count(*) FROM alerta").query(Integer.class).single();

		hallazgos(null, null)
				.andExpect(jsonPath("$[0].paciente.id").value(paciente.toString()))
				.andExpect(jsonPath("$[0].paciente.nombres").value("María"))
				.andExpect(jsonPath("$[0].paciente.apellidos").value("Soto Rojas"))
				.andExpect(jsonPath("$[0].variable").value("SPO2"))
				.andExpect(jsonPath("$[0].valor").value(85.0))
				.andExpect(jsonPath("$[0].fechaHoraMedicion").value(medicion.toString()))
				.andExpect(jsonPath("$[0].limiteGrave").value(88));
		assertThat(jdbc.sql("SELECT count(*) FROM alerta").query(Integer.class).single()).isEqualTo(alertasAntes);
	}

	// ---------- H7: permisos y auditoría ----------

	/** H7: sin sesión, 401; sin REPORTES:VER (rol PERSONAL), 403. */
	@Test
	void exigeReportesVer() throws Exception {
		usuario(RUN_PERSONAL, "PERSONAL", "Diego");

		mvc.perform(get("/api/hallazgos")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/hallazgos").header("Authorization", "Bearer " + token(RUN_PERSONAL, "Diego")))
				.andExpect(status().isForbidden());
	}

	/** H7: la consulta queda registrada como lectura de un reporte. */
	@Test
	void laLecturaQuedaAuditada() throws Exception {
		hallazgos(null, null).andExpect(status().isOk());

		assertThat(jdbc.sql("""
				SELECT count(*) FROM registro_auditoria WHERE accion = 'LEER' AND entidad = 'REPORTE'""")
				.query(Integer.class).single()).isEqualTo(1);
	}

	// ---------- Apoyo ----------

	/** Pide los hallazgos; desde y hasta van solo si no son null. */
	private ResultActions hallazgos(LocalDate desde, LocalDate hasta) throws Exception {
		var peticion = get("/api/hallazgos").header("Authorization", "Bearer " + token);
		if (desde != null) {
			peticion.param("desde", desde.toString());
		}
		if (hasta != null) {
			peticion.param("hasta", hasta.toString());
		}
		return mvc.perform(peticion);
	}

	/** @return el instante tantas horas antes de ahora */
	private Instant horasAtras(int horas) {
		return ahora.minus(Duration.ofHours(horas));
	}

	/** Guarda una lectura del reloj vigente, en su propio lote. */
	private void lectura(String tipo, double valor, Instant medicion) {
		UUID lote = jdbc.sql("""
				INSERT INTO sincronizacion (event_id, id_asignacion, hash_configuracion, fecha_hora_recepcion,
					nivel_bateria, reloj_puesto, tipo_conexion, version_app)
				VALUES (gen_random_uuid(), :a, 'h', :t, 80, true, 'LTE', '1.0.0') RETURNING id_sincronizacion""")
				.param("a", asignacion).param("t", Timestamp.from(medicion.plusSeconds(60)))
				.query(UUID.class).single();
		jdbc.sql("""
				INSERT INTO registro_vital (id_sincronizacion, id_asignacion, tipo, valor, fecha_hora_medicion)
				VALUES (:s, :a, :tipo, :v, :m)""")
				.param("s", lote).param("a", asignacion).param("tipo", tipo).param("v", valor)
				.param("m", Timestamp.from(medicion)).update();
	}

	/**
	 * Una alerta del reloj vigente detectada a esa hora.
	 *
	 * @param cancelada si el paciente la canceló en el reloj
	 * @param simulacro si es un ensayo
	 */
	private void alertaDelReloj(String tipo, Instant deteccion, boolean cancelada, boolean simulacro) {
		jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					fecha_hora_deteccion, fecha_hora_recepcion, fecha_hora_cancelacion, es_simulacro)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', :t, 'GRAVE', :d, :d, :c, :s)""")
				.param("a", asignacion).param("t", tipo).param("d", Timestamp.from(deteccion))
				.param("c", cancelada ? Timestamp.from(deteccion.plusSeconds(20)) : null)
				.param("s", simulacro).update();
	}

	/** Un umbral propio de FC de la paciente principal, vigente desde esa hora. */
	private void umbralFc(Instant desde, int graveBajo, int normalMin, int normalMax, int graveAlto) {
		umbralFc(desde, graveBajo, normalMin, normalMax, graveAlto, paciente);
	}

	/** Un umbral propio de FC de ese paciente, vigente desde esa hora. */
	private void umbralFc(Instant desde, int graveBajo, int normalMin, int normalMax, int graveAlto,
			UUID idPaciente) {
		jdbc.sql("""
				INSERT INTO umbral (id_paciente, variable, vigente_desde, grave_bajo, normal_min, normal_max,
					grave_alto)
				VALUES (:p, 'FC', :d, :gb, :nmin, :nmax, :ga)""")
				.param("p", idPaciente).param("d", Timestamp.from(desde)).param("gb", graveBajo)
				.param("nmin", normalMin).param("nmax", normalMax).param("ga", graveAlto).update();
	}

	/** @return un paciente con sus datos cifrados de verdad */
	private UUID paciente(String nombres, String apellidos) {
		return jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, nombres, apellidos, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, :hash, :nombres, :apellidos, CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar(nombres, ColumnaCifrada.PACIENTE_RUT))
				.param("hash", "hash-red-" + nombres)
				.param("nombres", cifrado.cifrar(nombres, ColumnaCifrada.PACIENTE_NOMBRES))
				.param("apellidos", cifrado.cifrar(apellidos, ColumnaCifrada.PACIENTE_APELLIDOS))
				.query(UUID.class).single();
	}

	/** @return el vínculo de un reloj nuevo con ese paciente, desde hace un mes */
	private UUID reloj(UUID idPaciente, String identificador) {
		UUID dispositivo = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:id, 'OPERATIVO')
				RETURNING id_dispositivo""").param("id", identificador).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, :hash) RETURNING id_asignacion""")
				.param("r", dispositivo).param("p", idPaciente)
				.param("inicio", Timestamp.from(ahora.minus(Duration.ofDays(30))))
				.param("hash", "hash-token-" + identificador)
				.query(UUID.class).single();
	}

	/** Crea una cuenta activa del equipo. */
	private void usuario(String run, String rol, String nombre) {
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :clave, true)""")
				.param("rol", rol).param("hash", hashRun.de(Run.de(run))).param("nombre", nombre)
				.param("correo", nombre.toLowerCase() + "@muni.cl")
				.param("clave", encoder.encode("Clave-de-" + nombre + "1")).update();
	}

	/** Inicia sesión y devuelve el token de acceso. */
	private String token(String run, String nombre) throws Exception {
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"Clave-de-" + nombre + "1\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.token");
	}

}
