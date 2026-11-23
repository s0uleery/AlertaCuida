package cl.ucn.alertacuida.tendencia;

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
 * Tendencias de FC y SpO2 de un usuario vistas desde afuera. Los casos salen del cronograma (3.5: la hora real
 * de cada medición y los eventos marcados) y de las decisiones del 19-11-2026, no del código. Sin Docker se
 * omite.
 *
 * <ul>
 * <li>T1 Hasta 2 días, cada lectura con su hora de medición, por variable y en orden; de todos los relojes del
 * usuario y de nadie más.</li>
 * <li>T2 Hasta 31 días, por hora de Chile: promedio, mínimo, máximo y cantidad.</li>
 * <li>T3 Hasta 90 días, por día de Chile.</li>
 * <li>T4 Trae las alertas del rango para marcarlas, con la hora de detección; sin simulacros; las canceladas
 * marcadas.</li>
 * <li>T5 Rango al revés o de más de 90 días, 400; usuario inexistente, 404.</li>
 * <li>T6 Exige REPORTES:VER y la lectura queda auditada con el id del usuario.</li>
 * <li>T7 Trae las ubicaciones con la misma agrupación: hasta 2 días todas; por hora o por día, la última de cada
 * una (decisión del 19-11-2026).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosTendenciasTest {

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

	/** Un día fijo en el pasado, para que las horas de Chile no dependan de cuándo corre la prueba. */
	private final LocalDate dia = LocalDate.now(ZonaHoraria.CHILE).minusDays(3);
	private UUID paciente;
	private UUID asignacion;
	private String token;

	/** Base vacía; una paciente con su reloj desde hace 100 días y una coordinadora con sesión. */
	@BeforeEach
	void preparar() throws Exception {
		BaseDePrueba.vaciar(jdbc);
		paciente = paciente("María");
		asignacion = reloj(paciente, "R-TEN-1");
		usuario(RUN_COORDINADORA, "COORDINADOR", "Camila");
		token = token(RUN_COORDINADORA, "Camila");
	}

	// ---------- T1: cada lectura ----------

	/**
	 * T1: en un día, cada lectura sale con su hora de medición, por variable y en orden; las de un reloj anterior
	 * del usuario también; las de otro usuario y las fuera del rango, no.
	 */
	@Test
	void hastaDosDiasCadaLectura() throws Exception {
		lectura("FC", 80, hora(10, 30));
		lectura("FC", 70, hora(10, 0));
		lectura("SPO2", 95, hora(10, 0));
		lectura("FC", 99, hora(10, 0).minus(Duration.ofDays(1)));
		UUID anterior = asignacion;
		asignacion = reloj(paciente, "R-TEN-2", hora(10, 45));
		lectura("FC", 90, hora(11, 0));
		asignacion = reloj(paciente("Otra"), "R-TEN-3");
		lectura("FC", 150, hora(11, 30));
		assertThat(anterior).isNotEqualTo(asignacion);

		tendencias(dia, dia).andExpect(status().isOk())
				.andExpect(jsonPath("$.agrupacion").value("LECTURA"))
				.andExpect(jsonPath("$.fc[*].promedio").value(contains(70.0, 80.0, 90.0)))
				.andExpect(jsonPath("$.fc[0].minimo").value(70.0))
				.andExpect(jsonPath("$.fc[0].maximo").value(70.0))
				.andExpect(jsonPath("$.fc[0].cantidad").value(1))
				.andExpect(jsonPath("$.fc[0].fechaHora").value(hora(10, 0).toString()))
				.andExpect(jsonPath("$.spo2[*].promedio").value(contains(95.0)));
	}

	// ---------- T2: por hora ----------

	/** T2: en una semana, las lecturas de la misma hora de Chile se juntan. */
	@Test
	void hastaTreintaYUnDiasPorHora() throws Exception {
		lectura("FC", 70, hora(10, 0));
		lectura("FC", 90, hora(10, 30));
		lectura("FC", 100, hora(11, 0));

		tendencias(dia.minusDays(6), dia).andExpect(jsonPath("$.agrupacion").value("HORA"))
				.andExpect(jsonPath("$.fc", hasSize(2)))
				.andExpect(jsonPath("$.fc[0].fechaHora").value(hora(10, 0).toString()))
				.andExpect(jsonPath("$.fc[0].promedio").value(80.0))
				.andExpect(jsonPath("$.fc[0].minimo").value(70.0))
				.andExpect(jsonPath("$.fc[0].maximo").value(90.0))
				.andExpect(jsonPath("$.fc[0].cantidad").value(2))
				.andExpect(jsonPath("$.fc[1].cantidad").value(1));
	}

	// ---------- T3: por día ----------

	/** T3: en 60 días, por día de Chile: las 23:30 y las 00:30 del día siguiente quedan en días distintos. */
	@Test
	void hastaNoventaDiasPorDia() throws Exception {
		lectura("SPO2", 90, hora(9, 0));
		lectura("SPO2", 96, hora(23, 30));
		lectura("SPO2", 97, hora(0, 30).plus(Duration.ofDays(1)));

		tendencias(dia.minusDays(59), dia.plusDays(1)).andExpect(jsonPath("$.agrupacion").value("DIA"))
				.andExpect(jsonPath("$.spo2", hasSize(2)))
				.andExpect(jsonPath("$.spo2[0].fechaHora").value(hora(0, 0).toString()))
				.andExpect(jsonPath("$.spo2[0].promedio").value(93.0))
				.andExpect(jsonPath("$.spo2[0].cantidad").value(2))
				.andExpect(jsonPath("$.spo2[1].promedio").value(97.0));
	}

	// ---------- T4: alertas ----------

	/** T4: las alertas del rango vienen en orden, con la cancelada marcada; sin simulacros ni las de otro día. */
	@Test
	void traeLasAlertasDelRango() throws Exception {
		UUID caida = alerta("CAIDA", hora(9, 0), false, false);
		UUID cancelada = alerta("FC", hora(12, 0), true, false);
		alerta("SPO2", hora(13, 0), false, true);
		alerta("FC", hora(9, 0).minus(Duration.ofDays(2)), false, false);

		tendencias(dia, dia).andExpect(jsonPath("$.alertas", hasSize(2)))
				.andExpect(jsonPath("$.alertas[0].id").value(caida.toString()))
				.andExpect(jsonPath("$.alertas[0].tipoEvento").value("CAIDA"))
				.andExpect(jsonPath("$.alertas[0].severidad").value("GRAVE"))
				.andExpect(jsonPath("$.alertas[0].fechaHoraDeteccion").value(hora(9, 0).toString()))
				.andExpect(jsonPath("$.alertas[0].cancelada").value(false))
				.andExpect(jsonPath("$.alertas[1].id").value(cancelada.toString()))
				.andExpect(jsonPath("$.alertas[1].cancelada").value(true));
	}

	// ---------- T7: ubicaciones ----------

	/** T7: hasta 2 días, todas las posiciones en orden; por hora, la última de cada hora de Chile. */
	@Test
	void lasUbicacionesConLaMismaAgrupacion() throws Exception {
		ubicacion(-29.90, -71.25, 15.0, hora(10, 0));
		ubicacion(-29.91, -71.26, null, hora(10, 45));
		ubicacion(-29.92, -71.27, 8.0, hora(11, 15));

		tendencias(dia, dia).andExpect(jsonPath("$.ubicaciones", hasSize(3)))
				.andExpect(jsonPath("$.ubicaciones[0].latitud").value(-29.90))
				.andExpect(jsonPath("$.ubicaciones[0].longitud").value(-71.25))
				.andExpect(jsonPath("$.ubicaciones[0].precisionMetros").value(15.0))
				.andExpect(jsonPath("$.ubicaciones[0].fechaHoraMedicion").value(hora(10, 0).toString()))
				.andExpect(jsonPath("$.ubicaciones[1].precisionMetros").doesNotExist());
		tendencias(dia.minusDays(6), dia).andExpect(jsonPath("$.ubicaciones", hasSize(2)))
				.andExpect(jsonPath("$.ubicaciones[0].latitud").value(-29.91))
				.andExpect(jsonPath("$.ubicaciones[1].latitud").value(-29.92));
	}

	// ---------- T5: rango y usuario ----------

	/** T5: rango al revés o de 91 días, 400; usuario inexistente, 404. */
	@Test
	void rangoInvalidoYUsuarioInexistente() throws Exception {
		tendencias(dia, dia.minusDays(1)).andExpect(status().isBadRequest());
		tendencias(dia.minusDays(90), dia).andExpect(status().isBadRequest());
		mvc.perform(get("/api/pacientes/" + UUID.randomUUID() + "/tendencias")
				.header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
	}

	// ---------- T6: permisos y auditoría ----------

	/** T6: sin sesión, 401; sin REPORTES:VER (rol PERSONAL), 403; la lectura queda auditada. */
	@Test
	void permisosYAuditoria() throws Exception {
		usuario(RUN_PERSONAL, "PERSONAL", "Diego");
		String ruta = "/api/pacientes/" + paciente + "/tendencias";

		mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
		mvc.perform(get(ruta).header("Authorization", "Bearer " + token(RUN_PERSONAL, "Diego")))
				.andExpect(status().isForbidden());
		tendencias(null, null).andExpect(status().isOk());

		assertThat(jdbc.sql("""
				SELECT count(*) FROM registro_auditoria
				WHERE accion = 'LEER' AND entidad = 'REPORTE' AND id_entidad = :p""")
				.param("p", paciente.toString()).query(Integer.class).single()).isEqualTo(1);
	}

	// ---------- Apoyo ----------

	/** Pide las tendencias de la paciente principal; desde y hasta van solo si no son null. */
	private ResultActions tendencias(LocalDate desde, LocalDate hasta) throws Exception {
		var peticion = get("/api/pacientes/" + paciente + "/tendencias").header("Authorization", "Bearer " + token);
		if (desde != null) {
			peticion.param("desde", desde.toString());
		}
		if (hasta != null) {
			peticion.param("hasta", hasta.toString());
		}
		return mvc.perform(peticion);
	}

	/** @return esa hora de Chile del día de la prueba */
	private Instant hora(int horas, int minutos) {
		return dia.atTime(horas, minutos).atZone(ZonaHoraria.CHILE).toInstant();
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

	/** Guarda una posición GPS del reloj vigente, en su propio lote. */
	private void ubicacion(double latitud, double longitud, Double precision, Instant medicion) {
		UUID lote = jdbc.sql("""
				INSERT INTO sincronizacion (event_id, id_asignacion, hash_configuracion, fecha_hora_recepcion,
					nivel_bateria, reloj_puesto, tipo_conexion, version_app)
				VALUES (gen_random_uuid(), :a, 'h', :t, 80, true, 'LTE', '1.0.0') RETURNING id_sincronizacion""")
				.param("a", asignacion).param("t", Timestamp.from(medicion.plusSeconds(60)))
				.query(UUID.class).single();
		jdbc.sql("""
				INSERT INTO ubicacion (id_sincronizacion, id_asignacion, latitud, longitud, precision_metros,
					fecha_hora_medicion)
				VALUES (:s, :a, :lat, :lon, :p, :m)""")
				.param("s", lote).param("a", asignacion).param("lat", latitud).param("lon", longitud)
				.param("p", precision).param("m", Timestamp.from(medicion)).update();
	}

	/** @return una alerta grave del reloj vigente, detectada a esa hora */
	private UUID alerta(String tipo, Instant deteccion, boolean cancelada, boolean simulacro) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					fecha_hora_deteccion, fecha_hora_recepcion, fecha_hora_cancelacion, es_simulacro)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', :t, 'GRAVE', :d, :d, :c, :s) RETURNING id_alerta""")
				.param("a", asignacion).param("t", tipo).param("d", Timestamp.from(deteccion))
				.param("c", cancelada ? Timestamp.from(deteccion.plusSeconds(20)) : null)
				.param("s", simulacro).query(UUID.class).single();
	}

	/** @return un paciente con su nombre cifrado */
	private UUID paciente(String nombres) {
		return jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, nombres, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, :hash, :nombres, CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar(nombres, ColumnaCifrada.PACIENTE_RUT))
				.param("hash", "hash-ten-" + nombres)
				.param("nombres", cifrado.cifrar(nombres, ColumnaCifrada.PACIENTE_NOMBRES))
				.query(UUID.class).single();
	}

	/**
	 * Vincula un reloj nuevo al paciente desde hace 100 días.
	 *
	 * @return el vínculo nuevo
	 */
	private UUID reloj(UUID idPaciente, String identificador) {
		return reloj(idPaciente, identificador, Instant.now().minus(Duration.ofDays(100)));
	}

	/**
	 * Vincula un reloj nuevo al paciente desde {@code inicio}. Si el paciente ya tenía uno vigente, lo cierra en
	 * ese mismo instante (un reemplazo): los dos vínculos quedan seguidos, sin cruzarse.
	 *
	 * @return el vínculo nuevo
	 */
	private UUID reloj(UUID idPaciente, String identificador, Instant inicio) {
		jdbc.sql("UPDATE asignacion_reloj SET fecha_fin = :fin WHERE id_paciente = :p AND fecha_fin IS NULL")
				.param("fin", Timestamp.from(inicio)).param("p", idPaciente).update();
		UUID dispositivo = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:id, 'OPERATIVO')
				RETURNING id_dispositivo""").param("id", identificador).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, :hash) RETURNING id_asignacion""")
				.param("r", dispositivo).param("p", idPaciente)
				.param("inicio", Timestamp.from(inicio))
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
