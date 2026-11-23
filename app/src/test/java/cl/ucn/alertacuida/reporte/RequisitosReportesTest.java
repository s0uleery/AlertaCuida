package cl.ucn.alertacuida.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
 * Reportes diarios y semanales de un usuario vistos desde afuera. Los casos salen del modelo de datos (se
 * calculan, no se guardan: promedios, máximos y mínimos de registro_vital y uso del reloj con minutos_puesto)
 * y de las decisiones del 19-11-2026, no del código. Sin Docker se omite.
 *
 * <ul>
 * <li>P1 FC y SpO2: promedio, mínimo, máximo y cantidad de lecturas del día de Chile.</li>
 * <li>P2 Uso del reloj: minutos puesto y sincronizaciones del día; batería mínima informada.</li>
 * <li>P3 Alertas por tipo y por gravedad actual, sin simulacros; las canceladas se cuentan aparte.</li>
 * <li>P4 Semanal: lunes a domingo de Chile, con la semana anterior para comparar.</li>
 * <li>P5 Un día sin datos da indicadores vacíos, sin error; usuario inexistente, 404.</li>
 * <li>P6 Exige REPORTES:VER y la lectura queda auditada con el id del usuario.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosReportesTest {

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

	/** Un miércoles en el pasado, para que la semana y las horas de Chile no dependan de cuándo corre. */
	private final LocalDate dia = LocalDate.now(ZonaHoraria.CHILE).minusWeeks(2)
			.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
	private UUID paciente;
	private UUID asignacion;
	private String token;

	/** Base vacía; una paciente con su reloj desde hace 60 días y una coordinadora con sesión. */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "registro_auditoria",
				"usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, nombres, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-rep-1', :nombres, CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.param("nombres", cifrado.cifrar("María", ColumnaCifrada.PACIENTE_NOMBRES))
				.query(UUID.class).single();
		UUID reloj = jdbc.sql("INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-REP-1', 'OPERATIVO') "
				+ "RETURNING id_dispositivo").query(UUID.class).single();
		asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, 'hash-token-rep') RETURNING id_asignacion""")
				.param("r", reloj).param("p", paciente)
				.param("inicio", Timestamp.from(Instant.now().minus(Duration.ofDays(60))))
				.query(UUID.class).single();
		usuario(RUN_COORDINADORA, "COORDINADOR", "Camila");
		token = token(RUN_COORDINADORA, "Camila");
	}

	// ---------- P1: FC y SpO2 ----------

	/** P1: solo cuentan las lecturas del día de Chile (23:59 sí; 00:00 del día siguiente no). */
	@Test
	void signosVitalesDelDia() throws Exception {
		UUID lote = sincronizacion(hora(dia, 12, 0), 80, 100);
		lectura(lote, "FC", 70, hora(dia, 8, 0));
		lectura(lote, "FC", 90, hora(dia, 23, 59));
		lectura(lote, "FC", 200, hora(dia.plusDays(1), 0, 0));
		lectura(lote, "SPO2", 95, hora(dia, 9, 0));

		diario(dia).andExpect(status().isOk())
				.andExpect(jsonPath("$.periodo").value("DIARIO"))
				.andExpect(jsonPath("$.desde").value(dia.toString()))
				.andExpect(jsonPath("$.hasta").value(dia.toString()))
				.andExpect(jsonPath("$.indicadores.fc.promedio").value(80.0))
				.andExpect(jsonPath("$.indicadores.fc.minimo").value(70.0))
				.andExpect(jsonPath("$.indicadores.fc.maximo").value(90.0))
				.andExpect(jsonPath("$.indicadores.fc.lecturas").value(2))
				.andExpect(jsonPath("$.indicadores.spo2.lecturas").value(1))
				.andExpect(jsonPath("$.anterior").doesNotExist());
	}

	// ---------- P2: uso del reloj y batería ----------

	/** P2: minutos puesto sumados, sincronizaciones contadas y la batería más baja del día. */
	@Test
	void usoDelRelojYBateria() throws Exception {
		sincronizacion(hora(dia, 8, 0), 90, 110);
		sincronizacion(hora(dia, 10, 0), 35, 120);
		sincronizacion(hora(dia.plusDays(1), 8, 0), 5, 120);

		diario(dia).andExpect(jsonPath("$.indicadores.uso.minutosPuesto").value(230))
				.andExpect(jsonPath("$.indicadores.uso.sincronizaciones").value(2))
				.andExpect(jsonPath("$.indicadores.bateriaMinima").value(35));
	}

	// ---------- P3: alertas ----------

	/** P3: por tipo y gravedad actual (una agravada cuenta como grave); sin simulacros; canceladas aparte. */
	@Test
	void alertasPorTipoYGravedad() throws Exception {
		alerta("CAIDA", "MEDIA", hora(dia, 9, 0), false, false, true);
		alerta("CAIDA", "LEVE", hora(dia, 10, 0), false, false, false);
		alerta("FC", "MEDIA", hora(dia, 11, 0), false, false, false);
		alerta("FC", "GRAVE", hora(dia, 12, 0), true, false, false);
		alerta("SPO2", "GRAVE", hora(dia, 13, 0), false, true, false);

		diario(dia).andExpect(jsonPath("$.indicadores.alertasPorTipo.CAIDA").value(2))
				.andExpect(jsonPath("$.indicadores.alertasPorTipo.FC").value(1))
				.andExpect(jsonPath("$.indicadores.alertasPorTipo.SPO2").doesNotExist())
				.andExpect(jsonPath("$.indicadores.alertasPorGravedad.GRAVE").value(1))
				.andExpect(jsonPath("$.indicadores.alertasPorGravedad.MEDIA").value(1))
				.andExpect(jsonPath("$.indicadores.alertasPorGravedad.LEVE").value(1))
				.andExpect(jsonPath("$.indicadores.alertasCanceladas").value(1));
	}

	// ---------- P4: semanal ----------

	/** P4: un miércoles elige su semana de lunes a domingo; la anterior viene para comparar. */
	@Test
	void semanalDeLunesADomingoConLaAnterior() throws Exception {
		LocalDate lunes = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		UUID lote = sincronizacion(hora(lunes, 12, 0), 80, 60);
		lectura(lote, "FC", 70, hora(lunes, 0, 0));
		lectura(lote, "FC", 80, hora(lunes.plusDays(6), 23, 59));
		lectura(lote, "FC", 100, hora(lunes.minusDays(1), 12, 0));

		mvc.perform(get("/api/pacientes/" + paciente + "/reportes/semanal").param("dia", dia.toString())
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.periodo").value("SEMANAL"))
				.andExpect(jsonPath("$.desde").value(lunes.toString()))
				.andExpect(jsonPath("$.hasta").value(lunes.plusDays(6).toString()))
				.andExpect(jsonPath("$.indicadores.fc.promedio").value(75.0))
				.andExpect(jsonPath("$.anterior.fc.promedio").value(100.0))
				.andExpect(jsonPath("$.anterior.fc.lecturas").value(1));
	}

	// ---------- P5: sin datos y usuario inexistente ----------

	/** P5: un día sin datos responde con todo vacío; un usuario que no existe, 404. */
	@Test
	void sinDatosYUsuarioInexistente() throws Exception {
		diario(dia).andExpect(status().isOk())
				.andExpect(jsonPath("$.indicadores.fc.lecturas").value(0))
				.andExpect(jsonPath("$.indicadores.fc.promedio").doesNotExist())
				.andExpect(jsonPath("$.indicadores.uso.minutosPuesto").value(0))
				.andExpect(jsonPath("$.indicadores.bateriaMinima").doesNotExist())
				.andExpect(jsonPath("$.indicadores.alertasCanceladas").value(0));
		mvc.perform(get("/api/pacientes/" + UUID.randomUUID() + "/reportes/diario")
				.header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
	}

	// ---------- P6: permisos y auditoría ----------

	/** P6: sin sesión, 401; sin REPORTES:VER, 403; la lectura queda auditada con el id del usuario. */
	@Test
	void permisosYAuditoria() throws Exception {
		usuario(RUN_PERSONAL, "PERSONAL", "Diego");
		String ruta = "/api/pacientes/" + paciente + "/reportes/diario";

		mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
		mvc.perform(get(ruta).header("Authorization", "Bearer " + token(RUN_PERSONAL, "Diego")))
				.andExpect(status().isForbidden());
		diario(dia).andExpect(status().isOk());

		assertThat(jdbc.sql("""
				SELECT count(*) FROM registro_auditoria
				WHERE accion = 'LEER' AND entidad = 'REPORTE' AND id_entidad = :p""")
				.param("p", paciente.toString()).query(Integer.class).single()).isEqualTo(1);
	}

	// ---------- Apoyo ----------

	/** Pide el reporte diario de ese día. */
	private ResultActions diario(LocalDate fecha) throws Exception {
		return mvc.perform(get("/api/pacientes/" + paciente + "/reportes/diario").param("dia", fecha.toString())
				.header("Authorization", "Bearer " + token));
	}

	/** @return esa hora de Chile de ese día */
	private static Instant hora(LocalDate fecha, int horas, int minutos) {
		return fecha.atTime(horas, minutos).atZone(ZonaHoraria.CHILE).toInstant();
	}

	/** @return un lote recibido a esa hora, con esa batería y esos minutos puesto */
	private UUID sincronizacion(Instant recepcion, int bateria, int minutosPuesto) {
		return jdbc.sql("""
				INSERT INTO sincronizacion (event_id, id_asignacion, hash_configuracion, fecha_hora_recepcion,
					nivel_bateria, reloj_puesto, minutos_puesto, tipo_conexion, version_app)
				VALUES (gen_random_uuid(), :a, 'h', :t, :b, true, :m, 'LTE', '1.0.0') RETURNING id_sincronizacion""")
				.param("a", asignacion).param("t", Timestamp.from(recepcion)).param("b", bateria)
				.param("m", minutosPuesto).query(UUID.class).single();
	}

	/** Guarda una lectura en ese lote. */
	private void lectura(UUID lote, String tipo, double valor, Instant medicion) {
		jdbc.sql("""
				INSERT INTO registro_vital (id_sincronizacion, id_asignacion, tipo, valor, fecha_hora_medicion)
				VALUES (:s, :a, :tipo, :v, :m)""")
				.param("s", lote).param("a", asignacion).param("tipo", tipo).param("v", valor)
				.param("m", Timestamp.from(medicion)).update();
	}

	/**
	 * Una alerta del reloj detectada a esa hora.
	 *
	 * @param agravada si después pasó a grave
	 */
	private void alerta(String tipo, String severidad, Instant deteccion, boolean cancelada, boolean simulacro,
			boolean agravada) {
		jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					fecha_hora_deteccion, fecha_hora_recepcion, fecha_hora_cancelacion, es_simulacro,
					fecha_hora_agravamiento)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', :t, :s, :d, :d, :c, :sim, :g)""")
				.param("a", asignacion).param("t", tipo).param("s", severidad).param("d", Timestamp.from(deteccion))
				.param("c", cancelada ? Timestamp.from(deteccion.plusSeconds(20)) : null)
				.param("sim", simulacro)
				.param("g", agravada ? Timestamp.from(deteccion.plusSeconds(600)) : null).update();
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
