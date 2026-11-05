package cl.ucn.alertacuida.reloj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.configuracion.ConfiguracionDelReloj;
import cl.ucn.alertacuida.application.configuracion.ConsultarConfiguracionAplicada;
import cl.ucn.alertacuida.application.configuracion.modelo.ConfiguracionAplicada;
import cl.ucn.alertacuida.application.configuracion.modelo.EstadoConfiguracion;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * API del reloj de punta a punta: autenticación con el token del reloj, sincronización, alertas
 * y cancelaciones, contra un PostgreSQL real (incluidas sus restricciones). Sin Docker se omite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class ApiRelojTest {

	private static final String TOKEN = "token-del-reloj-de-prueba";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private SecretosUnSoloUso secretos;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private ConfiguracionDelReloj configuraciones;
	@Autowired
	private ConsultarConfiguracionAplicada aplicada;

	private Instant ahora;
	private UUID paciente;
	private UUID asignacion;

	/** Base vacía con un paciente que usa un reloj desde hace un día, con el token {@value #TOKEN}. */
	@BeforeEach
	void preparar() {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		// RUN cifrado de verdad: la configuración del reloj lee al paciente
		paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-paciente', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-001', 'OPERATIVO')
				RETURNING id_dispositivo""")
				.query(UUID.class).single();
		asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:reloj, :paciente, :inicio, :hash) RETURNING id_asignacion""")
				.param("reloj", reloj)
				.param("paciente", paciente)
				.param("inicio", Timestamp.from(ahora.minus(1, ChronoUnit.DAYS)))
				.param("hash", secretos.hash(TOKEN))
				.query(UUID.class).single();
	}

	/** Sin token, con uno desconocido o con el vínculo cerrado responde 401 TOKEN_INVALIDO. */
	@Test
	void sinTokenValidoResponde401() throws Exception {
		mvc.perform(post("/api/reloj/v1/sincronizaciones").contentType(MediaType.APPLICATION_JSON)
				.content(lote(UUID.randomUUID(), "")))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
		enviar("otro-token", "/api/reloj/v1/sincronizaciones", lote(UUID.randomUUID(), ""))
				.andExpect(status().isUnauthorized());

		jdbc.sql("UPDATE asignacion_reloj SET fecha_fin = :fin").param("fin", Timestamp.from(ahora)).update();

		enviar(TOKEN, "/api/reloj/v1/sincronizaciones", lote(UUID.randomUUID(), ""))
				.andExpect(status().isUnauthorized());
	}

	/** El token del reloj no abre el dashboard: no es un token del personal. */
	@Test
	void elTokenDelRelojNoAbreElDashboard() throws Exception {
		mvc.perform(get("/api/pacientes").header("Authorization", "Bearer " + TOKEN))
				.andExpect(status().isUnauthorized());
	}

	/** El lote se guarda; la lectura imposible se descarta sola; reenviarlo no duplica nada. */
	@Test
	void sincronizaGuardaElLoteYNoDuplicaAlReenviar() throws Exception {
		UUID eventId = UUID.randomUUID();
		String lecturas = """
				, "lecturas": [
					{"tipo": "FC", "valor": 72, "fechaHoraMedicion": "%s"},
					{"tipo": "SPO2", "valor": 96, "fechaHoraMedicion": "%s"},
					{"tipo": "FC", "valor": 400, "fechaHoraMedicion": "%s"}],
				"eventosDescartados": [{"tipoEvento": "FC", "cantidad": 2}]"""
				.formatted(hace(60), hace(60), hace(30));

		enviar(TOKEN, "/api/reloj/v1/sincronizaciones", lote(eventId, lecturas))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lecturasGuardadas").value(2))
				.andExpect(jsonPath("$.lecturasRechazadas", hasSize(1)))
				.andExpect(jsonPath("$.lecturasRechazadas[0].indice").value(2))
				.andExpect(jsonPath("$.lecturasRechazadas[0].motivo").value("FUERA_DE_RANGO"))
				.andExpect(jsonPath("$.loteRepetido").value(false));
		enviar(TOKEN, "/api/reloj/v1/sincronizaciones", lote(eventId, lecturas))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loteRepetido").value(true))
				.andExpect(jsonPath("$.lecturasGuardadas").value(2));

		assertThat(contar("sincronizacion")).isEqualTo(1);
		assertThat(contar("registro_vital")).isEqualTo(2);
		assertThat(contar("evento_descartado")).isEqualTo(1);
		Integer bateria = jdbc.sql("SELECT nivel_bateria FROM sincronizacion WHERE id_asignacion = :a")
				.param("a", asignacion).query(Integer.class).single();
		assertThat(bateria).isEqualTo(80);
	}

	/** Un mensaje mal formado responde 400 MENSAJE_INVALIDO y no guarda nada. */
	@Test
	void unMensajeMalFormadoResponde400() throws Exception {
		enviar(TOKEN, "/api/reloj/v1/sincronizaciones", lote(UUID.randomUUID(), "")
				.replace("\"nivelBateria\": 80", "\"nivelBateria\": 150"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("MENSAJE_INVALIDO"));
		enviar(TOKEN, "/api/reloj/v1/sincronizaciones", "{\"eventId\": \"no-es-uuid\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("MENSAJE_INVALIDO"));
		enviar(TOKEN, "/api/reloj/v1/alertas", alerta(UUID.randomUUID()).replace("CAIDA", "SOS"))
				.andExpect(status().isBadRequest());

		assertThat(contar("sincronizacion")).isZero();
		assertThat(contar("alerta")).isZero();
	}

	/** La alerta se guarda con su evidencia (201); el reenvío responde 200 con la misma alerta. */
	@Test
	void laAlertaSeGuardaUnaVezConSuEvidencia() throws Exception {
		UUID eventId = UUID.randomUUID();

		enviar(TOKEN, "/api/reloj/v1/alertas", alerta(eventId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado.estado").value("RECIBIDA"))
				.andExpect(jsonPath("$.estado.detenerEscalamiento").value(false))
				.andExpect(jsonPath("$.numero").isNumber());
		enviar(TOKEN, "/api/reloj/v1/alertas", alerta(eventId))
				.andExpect(status().isOk());
		mvc.perform(get("/api/reloj/v1/alertas/" + eventId).header("Authorization", "Bearer " + TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(eventId.toString()))
				.andExpect(jsonPath("$.severidadActual").value("GRAVE"));

		assertThat(contar("alerta")).isEqualTo(1);
		Integer inmovilidad = jdbc.sql("SELECT (evidencia ->> 'segundosInmovilidad')::int FROM alerta")
				.query(Integer.class).single();
		assertThat(inmovilidad).isEqualTo(60);
		String origen = jdbc.sql("SELECT origen FROM alerta").query(String.class).single();
		assertThat(origen).isEqualTo("RELOJ");
	}

	/** Una cancelación queda CANCELADA; una alerta desconocida responde 404 ALERTA_DESCONOCIDA. */
	@Test
	void laCancelacionQuedaCanceladaYUnaAlertaDesconocidaResponde404() throws Exception {
		UUID eventId = UUID.randomUUID();

		enviar(TOKEN, "/api/reloj/v1/cancelaciones", """
				{"eventId": "%s", "tipoEvento": "CAIDA", "severidad": "MEDIA", "versionConfiguracion": "v1",
				"fechaHoraDeteccion": "%s", "fechaHoraCancelacion": "%s"}"""
				.formatted(eventId, hace(2), hace(1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado.estado").value("CANCELADA"))
				.andExpect(jsonPath("$.estado.detenerEscalamiento").value(true));
		mvc.perform(get("/api/reloj/v1/alertas/" + UUID.randomUUID()).header("Authorization", "Bearer " + TOKEN))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("ALERTA_DESCONOCIDA"));
	}

	/**
	 * La configuración trae los valores base y el calendario, con su versión como ETag; si el reloj
	 * ya la tiene responde 304 sin cuerpo.
	 */
	@Test
	void laConfiguracionSeDescargaSoloSiCambio() throws Exception {
		String version = mvc.perform(get("/api/reloj/v1/configuracion").header("Authorization", "Bearer " + TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").isString())
				.andExpect(jsonPath("$.modulos.caidas").value(true))
				.andExpect(jsonPath("$.modulos.botonAyuda").value(true))
				.andExpect(jsonPath("$.modulos.zonaSegura").value(false))
				.andExpect(jsonPath("$.zonaSegura").doesNotExist())
				.andExpect(jsonPath("$.umbrales.fc.graveBajo").value(40))
				.andExpect(jsonPath("$.umbrales.spo2.normalMin").value(92))
				.andExpect(jsonPath("$.reglas.versionMatriz").value("PROVISIONAL"))
				.andExpect(jsonPath("$.reglas.tendencia.minutos").value(120))
				.andExpect(jsonPath("$.reglas.tendencia.severidad").value("MEDIA"))
				.andExpect(jsonPath("$.escalamiento.minutosEspera").value(5))
				.andExpect(jsonPath("$.escalamiento.rondas").value(2))
				.andExpect(jsonPath("$.contactos", hasSize(0)))
				.andExpect(jsonPath("$.calendario.zonaHoraria").value("America/Santiago"))
				.andExpect(jsonPath("$.calendario.horarioHabil[0].dia").value("LUNES"))
				.andExpect(jsonPath("$.calendario.horarioHabil[0].inicio").value("08:30"))
				.andReturn().getResponse().getHeader("ETag");
		assertThat(version).matches("\"[0-9a-f]{16}\"");

		mvc.perform(get("/api/reloj/v1/configuracion").header("Authorization", "Bearer " + TOKEN)
				.header("If-None-Match", version))
				.andExpect(status().isNotModified());
		mvc.perform(get("/api/reloj/v1/configuracion").header("Authorization", "Bearer " + TOKEN)
				.header("If-None-Match", "\"otra-version\""))
				.andExpect(status().isOk());
	}

	/**
	 * La sincronización avisa si el reloj debe descargar la configuración, y el dashboard ve desde
	 * cuándo el reloj aplica la vigente.
	 */
	@Test
	void laSincronizacionAvisaSiHayConfiguracionNueva() throws Exception {
		String vigente = configuraciones.de(paciente);
		UUID antigua = UUID.randomUUID();
		UUID primeraAlDia = UUID.randomUUID();

		enviar(TOKEN, "/api/reloj/v1/sincronizaciones", lote(antigua, ""))
				.andExpect(jsonPath("$.configuracionNueva").value(true))
				.andExpect(jsonPath("$.versionConfiguracion").value(vigente));
		assertThat(aplicada.consultar(paciente).estado()).isEqualTo(EstadoConfiguracion.PENDIENTE);

		enviar(TOKEN, "/api/reloj/v1/sincronizaciones", conVersion(lote(primeraAlDia, ""), vigente))
				.andExpect(jsonPath("$.configuracionNueva").value(false));
		enviar(TOKEN, "/api/reloj/v1/sincronizaciones", conVersion(lote(UUID.randomUUID(), ""), vigente))
				.andExpect(jsonPath("$.configuracionNueva").value(false));

		ConfiguracionAplicada alDia = aplicada.consultar(paciente);
		assertThat(alDia.estado()).isEqualTo(EstadoConfiguracion.AL_DIA);
		assertThat(alDia.aplicadaDesde()).isEqualTo(recepcion(primeraAlDia));
	}

	/** El token de Firebase se guarda en el reloj; uno vacío responde 400. */
	@Test
	void guardaElTokenDeFirebase() throws Exception {
		mvc.perform(put("/api/reloj/v1/token-fcm").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"token-de-firebase\"}"))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/reloj/v1/token-fcm").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\": \" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("MENSAJE_INVALIDO"));

		assertThat(jdbc.sql("SELECT token_fcm FROM smartwatch").query(String.class).single())
				.isEqualTo("token-de-firebase");
		assertThat(jdbc.sql("SELECT fecha_token_fcm IS NOT NULL FROM smartwatch").query(Boolean.class).single())
				.isTrue();
	}

	/** El reloj envía con su token y cuerpo JSON. */
	private ResultActions enviar(String token, String ruta, String cuerpo) throws Exception {
		return mvc.perform(post(ruta).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/**
	 * @param resto campos adicionales del JSON, empezando con coma (o vacío)
	 * @return un lote con el estado de un reloj normal
	 */
	private String lote(UUID eventId, String resto) {
		return """
				{"eventId": "%s", "fechaHoraEnvio": "%s",
				"estado": {"nivelBateria": 80, "relojPuesto": true, "minutosPuesto": 120, "tipoConexion": "LTE",
				"versionApp": "1.0.0", "versionConfiguracion": "v1"}%s}"""
				.formatted(eventId, ahora, resto);
	}

	/** @return el lote, con el reloj informando esa versión de configuración en vez de "v1" */
	private static String conVersion(String lote, String version) {
		return lote.replace("\"versionConfiguracion\": \"v1\"", "\"versionConfiguracion\": \"" + version + "\"");
	}

	/** @return cuándo llegó el lote con ese eventId */
	private Instant recepcion(UUID eventId) {
		return jdbc.sql("SELECT fecha_hora_recepcion FROM sincronizacion WHERE event_id = :e").param("e", eventId)
				.query(Timestamp.class).single().toInstant();
	}

	/** @return una caída grave con evidencia */
	private String alerta(UUID eventId) {
		return """
				{"eventId": "%s", "tipoEvento": "CAIDA", "severidad": "GRAVE", "versionConfiguracion": "v1",
				"fechaHoraDeteccion": "%s", "fechaHoraEnvio": "%s", "esperaDiaHabil": false,
				"evidencia": {"segundosInmovilidad": 60, "nivelBateria": 70}}"""
				.formatted(eventId, hace(1), ahora);
	}

	/** @return la hora de hace tantos minutos, en ISO */
	private String hace(long minutos) {
		return ahora.minus(minutos, ChronoUnit.MINUTES).toString();
	}

	/** @return cuántas filas tiene la tabla */
	private long contar(String tabla) {
		return jdbc.sql("SELECT count(*) FROM " + tabla).query(Long.class).single();
	}

}
