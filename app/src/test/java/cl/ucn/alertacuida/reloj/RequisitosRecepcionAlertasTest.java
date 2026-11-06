package cl.ucn.alertacuida.reloj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * Tarea 2.1 vista desde afuera: cada caso sale de un requisito escrito (cronograma, contrato de
 * docs/api-reloj.yaml, notas del modelo o decisiones del 05-11-2026), no del código. Todo pasa por la
 * API del reloj; la base solo se usa para preparar datos y revisar lo guardado. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 "El servidor nunca descarta una alerta": si no calza, la acepta y marca la discrepancia.</li>
 * <li>R2 Discrepancias: versión distinta, módulo apagado, gravedad distinta de la configurada y
 * lecturas que no superan los umbrales.</li>
 * <li>R3 Umbrales: grave es "bajo" grave_bajo o "sobre" grave_alto, estricto (130 no es grave en FC);
 * normal_min y normal_max son normales.</li>
 * <li>R4 Desde la 10.ª alerta del mismo reloj en 60 minutos se crea una alerta técnica leve
 * EXCESO_ALERTAS; una abierta a la vez; si el equipo la cerró, puede crearse otra. Las alertas que el
 * paciente canceló no cuentan, y una de hace justo 60 minutos ya no entra en la ventana.</li>
 * <li>R5 Agravamiento: solo caídas leves o medias; repetirlo no cambia la hora; 404 si no es de ese
 * reloj; 409 NO_APLICA si no aplica.</li>
 * <li>R6 Reintento con el mismo eventId: 200 con la misma alerta, sin duplicar nada.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosRecepcionAlertasTest {

	private static final String TOKEN = "token-reloj-requisitos";
	private static final String TOKEN_OTRO = "token-otro-reloj-requisitos";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private SecretosUnSoloUso secretos;
	@Autowired
	private CifradoCampos cifrado;

	private Instant ahora;
	private UUID paciente;
	private UUID asignacion;
	/** La versión de configuración que el servidor entrega hoy al reloj (ETag de GET /configuracion). */
	private String vigente;

	/** Base vacía; un paciente con su reloj, y otro reloj de otro paciente para los casos ajenos. */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		paciente = paciente("12345678-5", "hash-requisitos-1");
		asignacion = reloj("R-REQ-1", paciente, TOKEN);
		reloj("R-REQ-2", paciente("11111111-1", "hash-requisitos-2"), TOKEN_OTRO);
		String etag = mvc.perform(get("/api/reloj/v1/configuracion").header("Authorization", "Bearer " + TOKEN))
				.andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
		vigente = etag.replace("\"", "");
	}

	// ---------- R1 y R2: nunca se descarta; lo que no calza queda marcado ----------

	/** R2: una alerta que calza en todo no queda con discrepancias. */
	@Test
	void unaAlertaQueCalzaNoTieneDiscrepancias() throws Exception {
		UUID eventId = UUID.randomUUID();
		alerta(eventId, "FC", "GRAVE", vigente, lecturas("FC", 140, "FC", 145)).andExpect(status().isCreated());

		assertThat(discrepancias(eventId)).isEmpty();
	}

	/** R1 y R2: con otra versión se acepta (201) y queda VERSION_DISTINTA. */
	@Test
	void otraVersionSeAceptaYQuedaMarcada() throws Exception {
		UUID eventId = UUID.randomUUID();
		alerta(eventId, "CAIDA", "MEDIA", "version-vieja", null).andExpect(status().isCreated());

		assertThat(discrepancias(eventId)).containsExactly("VERSION_DISTINTA");
	}

	/** R1 y R2: una alerta de un módulo apagado se acepta y queda MODULO_APAGADO. */
	@Test
	void moduloApagadoSeAceptaYQuedaMarcado() throws Exception {
		jdbc.sql("""
				INSERT INTO configuracion (id_paciente, vigente_desde, tendencia_activa)
				VALUES (:p, now() - interval '1 hour', false)""").param("p", paciente).update();
		String version = versionVigente();
		UUID eventId = UUID.randomUUID();

		alerta(eventId, "TENDENCIA", "MEDIA", version, lecturas("FC", 120)).andExpect(status().isCreated());

		assertThat(discrepancias(eventId)).containsExactly("MODULO_APAGADO");
	}

	/** R2: la tendencia está configurada MEDIA (application.properties); si llega GRAVE, queda marcada. */
	@Test
	void tendenciaConOtraGravedadQuedaMarcada() throws Exception {
		UUID eventId = UUID.randomUUID();
		alerta(eventId, "TENDENCIA", "GRAVE", vigente, lecturas("FC", 120)).andExpect(status().isCreated());

		assertThat(discrepancias(eventId)).containsExactly("GRAVEDAD_DISTINTA");
	}

	/** R1: varias cosas no calzan a la vez; la alerta se acepta y quedan todas. */
	@Test
	void variasDiscrepanciasALaVez() throws Exception {
		UUID eventId = UUID.randomUUID();
		alerta(eventId, "TENDENCIA", "GRAVE", "version-vieja", lecturas("FC", 80)).andExpect(status().isCreated());

		assertThat(discrepancias(eventId))
				.containsExactlyInAnyOrder("VERSION_DISTINTA", "GRAVEDAD_DISTINTA", "LECTURAS_EN_RANGO");
	}

	// ---------- R3: límites de los umbrales (valores base FC 40/50/110/130, SpO2 88/92) ----------

	/** R3: en FC, 130 y 40 no son graves ("sobre" y "bajo" estrictos); 131 y 39 sí. */
	@Test
	void fcEnLosLimitesGraves() throws Exception {
		assertThat(lecturaEnRango("FC", "FC", 130)).isTrue();
		assertThat(lecturaEnRango("FC", "FC", 40)).isTrue();
		assertThat(lecturaEnRango("FC", "FC", 131)).isFalse();
		assertThat(lecturaEnRango("FC", "FC", 39)).isFalse();
	}

	/** R3: en SpO2, 88 no es grave; 87 sí. SpO2 no tiene límite alto. */
	@Test
	void spo2EnElLimiteGrave() throws Exception {
		assertThat(lecturaEnRango("SPO2", "SPO2", 88)).isTrue();
		assertThat(lecturaEnRango("SPO2", "SPO2", 87)).isFalse();
	}

	/** R3: en la tendencia, 110 y 50 de FC y 92 de SpO2 son normales; 111, 49 y 91 no. */
	@Test
	void tendenciaEnLosLimitesNormales() throws Exception {
		assertThat(lecturaEnRango("TENDENCIA", "FC", 110)).isTrue();
		assertThat(lecturaEnRango("TENDENCIA", "FC", 50)).isTrue();
		assertThat(lecturaEnRango("TENDENCIA", "SPO2", 92)).isTrue();
		assertThat(lecturaEnRango("TENDENCIA", "FC", 111)).isFalse();
		assertThat(lecturaEnRango("TENDENCIA", "FC", 49)).isFalse();
		assertThat(lecturaEnRango("TENDENCIA", "SPO2", 91)).isFalse();
	}

	// ---------- R4: exceso de alertas ----------

	/** R4: con 9 alertas en la hora no pasa nada; la 10.ª crea una técnica leve del servidor. */
	@Test
	void laDecimaAlertaCreaLaTecnica() throws Exception {
		for (int i = 0; i < 9; i++) {
			alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());
		}
		assertThat(tecnicas()).isZero();

		alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());

		assertThat(tecnicas()).isEqualTo(1);
		assertThat(jdbc.sql("""
				SELECT origen || ' ' || severidad FROM alerta WHERE tipo_evento = 'EXCESO_ALERTAS'""")
				.query(String.class).single()).isEqualTo("SERVIDOR LEVE");
	}

	/** R1 y R4: pasado el límite se siguen aceptando todas, con una sola técnica abierta. */
	@Test
	void pasadoElLimiteSeAceptanTodasConUnaSolaTecnica() throws Exception {
		for (int i = 0; i < 13; i++) {
			alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());
		}

		assertThat(alertasDelReloj()).isEqualTo(13);
		assertThat(tecnicas()).isEqualTo(1);
	}

	/** R4: si el equipo cerró la técnica y el reloj sigue enviando, se crea otra. */
	@Test
	void cerradaLaTecnicaSePuedeCrearOtra() throws Exception {
		for (int i = 0; i < 10; i++) {
			alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null);
		}
		jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, fecha_hora_cierre)
				SELECT id_alerta, now() FROM alerta WHERE tipo_evento = 'EXCESO_ALERTAS'""").update();

		alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());

		assertThat(tecnicas()).isEqualTo(2);
	}

	/** R4: solo cuentan los últimos 60 minutos: 9 de hace 61 min y una nueva no llegan al límite. */
	@Test
	void lasAlertasViejasNoCuentan() throws Exception {
		alertasGuardadas(9, 61);

		alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());

		assertThat(tecnicas()).isZero();
	}

	/** R4: 9 de hace 59 min y una nueva sí son 10 en la ventana. */
	@Test
	void lasAlertasDeLaUltimaHoraCuentan() throws Exception {
		alertasGuardadas(9, 59);

		alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());

		assertThat(tecnicas()).isEqualTo(1);
	}

	/** R4: una alerta de hace justo 60 minutos ya no entra: 9 así y una nueva no llegan al límite. */
	@Test
	void unaAlertaDeHaceJusto60MinutosNoEntra() throws Exception {
		alertasGuardadas(9, 60);

		alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());

		assertThat(tecnicas()).isZero();
	}

	/**
	 * R4: las cancelaciones del paciente no cuentan. Con 9 alertas, 5 cancelaciones no crean la técnica;
	 * la siguiente alerta enviada es la 10.ª y sí la crea.
	 */
	@Test
	void lasCancelacionesNoCuentanParaElLimite() throws Exception {
		for (int i = 0; i < 9; i++) {
			alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());
		}
		for (int i = 0; i < 5; i++) {
			cancelacion(UUID.randomUUID()).andExpect(status().isCreated());
		}
		assertThat(tecnicas()).isZero();

		alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null).andExpect(status().isCreated());

		assertThat(tecnicas()).isEqualTo(1);
	}

	/** R4: el límite es por reloj: las alertas de otro reloj no se suman. */
	@Test
	void elLimiteEsPorReloj() throws Exception {
		for (int i = 0; i < 5; i++) {
			alerta(UUID.randomUUID(), "CAIDA", "MEDIA", vigente, null);
			alertaCon(TOKEN_OTRO, UUID.randomUUID(), "CAIDA", "MEDIA", "v", null);
		}

		assertThat(tecnicas()).isZero();
	}

	// ---------- R5: agravamiento ----------

	/** R5: una caída leve y una media se agravan; queda GRAVE con la hora y la inmovilidad. */
	@Test
	void lasCaidasLevesYMediasSeAgravan() throws Exception {
		for (String severidad : List.of("LEVE", "MEDIA")) {
			UUID caida = UUID.randomUUID();
			alerta(caida, "CAIDA", severidad, vigente, null).andExpect(status().isCreated());

			agravar(TOKEN, caida, ahora, 75).andExpect(status().isOk())
					.andExpect(jsonPath("$.severidadActual").value("GRAVE"));

			assertThat(jdbc.sql("""
					SELECT segundos_inmovilidad_agravamiento FROM alerta WHERE event_id = :e""")
					.param("e", caida).query(Integer.class).single()).isEqualTo(75);
		}
	}

	/** R5: repetir el agravamiento responde 200 y no cambia la hora ya registrada. */
	@Test
	void repetirElAgravamientoNoCambiaLaHora() throws Exception {
		UUID caida = UUID.randomUUID();
		alerta(caida, "CAIDA", "MEDIA", vigente, null);
		agravar(TOKEN, caida, ahora, 60).andExpect(status().isOk());

		agravar(TOKEN, caida, ahora.plusSeconds(300), 360).andExpect(status().isOk());

		assertThat(jdbc.sql("SELECT fecha_hora_agravamiento FROM alerta WHERE event_id = :e").param("e", caida)
				.query(Timestamp.class).single().toInstant()).isEqualTo(ahora);
	}

	/** R5: 409 NO_APLICA si no es caída, si llegó grave o si el paciente la canceló. */
	@Test
	void elAgravamientoQueNoAplicaResponde409() throws Exception {
		UUID fc = UUID.randomUUID();
		UUID grave = UUID.randomUUID();
		UUID cancelada = UUID.randomUUID();
		alerta(fc, "FC", "MEDIA", vigente, lecturas("FC", 140));
		alerta(grave, "CAIDA", "GRAVE", vigente, null);
		mvc.perform(post("/api/reloj/v1/cancelaciones").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"eventId": "%s", "tipoEvento": "CAIDA", "severidad": "MEDIA", "versionConfiguracion": "%s",
						"fechaHoraDeteccion": "%s", "fechaHoraCancelacion": "%s"}"""
						.formatted(cancelada, vigente, ahora.minusSeconds(40), ahora.minusSeconds(20))))
				.andExpect(status().isCreated());

		for (UUID eventId : List.of(fc, grave, cancelada)) {
			agravar(TOKEN, eventId, ahora, 60).andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("NO_APLICA"));
		}
	}

	/** R5: 404 ALERTA_DESCONOCIDA si la caída es de otro reloj o no existe. */
	@Test
	void elAgravamientoAjenoODesconocidoResponde404() throws Exception {
		UUID caida = UUID.randomUUID();
		alerta(caida, "CAIDA", "MEDIA", vigente, null);

		agravar(TOKEN_OTRO, caida, ahora, 60).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("ALERTA_DESCONOCIDA"));
		agravar(TOKEN, UUID.randomUUID(), ahora, 60).andExpect(status().isNotFound());
	}

	/** Contrato: segundosInmovilidad tiene mínimo 0; un valor negativo es un mensaje mal formado (400). */
	@Test
	void inmovilidadNegativaResponde400() throws Exception {
		UUID caida = UUID.randomUUID();
		alerta(caida, "CAIDA", "MEDIA", vigente, null);

		agravar(TOKEN, caida, ahora, -5).andExpect(status().isBadRequest());
	}

	// ---------- R6: reintentos ----------

	/** R6: reenviar la misma alerta responde 200 y no duplica la alerta ni sus discrepancias. */
	@Test
	void unReintentoNoDuplicaNada() throws Exception {
		UUID eventId = UUID.randomUUID();
		alerta(eventId, "CAIDA", "MEDIA", "version-vieja", null).andExpect(status().isCreated());

		alerta(eventId, "CAIDA", "MEDIA", "version-vieja", null).andExpect(status().isOk());

		assertThat(alertasDelReloj()).isEqualTo(1);
		assertThat(discrepancias(eventId)).containsExactly("VERSION_DISTINTA");
	}

	// ---------- Ayudas ----------

	/** @return si una alerta de ese tipo con una sola lectura queda marcada LECTURAS_EN_RANGO */
	private boolean lecturaEnRango(String tipoAlerta, String variable, double valor) throws Exception {
		UUID eventId = UUID.randomUUID();
		String severidad = "TENDENCIA".equals(tipoAlerta) ? "MEDIA" : "GRAVE";
		alerta(eventId, tipoAlerta, severidad, vigente, lecturas(variable, valor)).andExpect(status().isCreated());
		return discrepancias(eventId).contains("LECTURAS_EN_RANGO");
	}

	/** @return la evidencia con esas lecturas, dadas como pares variable, valor */
	private String lecturas(Object... pares) {
		StringBuilder texto = new StringBuilder("{\"lecturas\": [");
		for (int i = 0; i < pares.length; i += 2) {
			texto.append(i == 0 ? "" : ", ").append("{\"tipo\": \"").append(pares[i]).append("\", \"valor\": ")
					.append(pares[i + 1]).append(", \"fechaHoraMedicion\": \"").append(ahora.minusSeconds(120))
					.append("\"}");
		}
		return texto.append("]}").toString();
	}

	/** Envía una alerta con el token del reloj del paciente. */
	private ResultActions alerta(UUID eventId, String tipo, String severidad, String version, String evidencia)
			throws Exception {
		return alertaCon(TOKEN, eventId, tipo, severidad, version, evidencia);
	}

	/** Envía una alerta con ese token, detectada hace 40 s y enviada hace 10 s. */
	private ResultActions alertaCon(String token, UUID eventId, String tipo, String severidad, String version,
			String evidencia) throws Exception {
		String cuerpo = """
				{"eventId": "%s", "tipoEvento": "%s", "severidad": "%s", "versionConfiguracion": "%s",
				"fechaHoraDeteccion": "%s", "fechaHoraEnvio": "%s", "esperaDiaHabil": false%s}"""
				.formatted(eventId, tipo, severidad, version, ahora.minusSeconds(40), ahora.minusSeconds(10),
						evidencia == null ? "" : ", \"evidencia\": " + evidencia);
		return mvc.perform(post("/api/reloj/v1/alertas").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** Informa una caída que el paciente canceló en los 30 s, con el token del reloj del paciente. */
	private ResultActions cancelacion(UUID eventId) throws Exception {
		return mvc.perform(post("/api/reloj/v1/cancelaciones").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"eventId": "%s", "tipoEvento": "CAIDA", "severidad": "MEDIA", "versionConfiguracion": "%s",
						"fechaHoraDeteccion": "%s", "fechaHoraCancelacion": "%s"}"""
						.formatted(eventId, vigente, ahora.minusSeconds(40), ahora.minusSeconds(20))));
	}

	/** Pide el agravamiento de una caída. */
	private ResultActions agravar(String token, UUID eventId, Instant fecha, int segundos) throws Exception {
		return mvc.perform(post("/api/reloj/v1/alertas/" + eventId + "/agravamiento")
				.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaHoraAgravamiento\": \"%s\", \"segundosInmovilidad\": %d}"
						.formatted(fecha, segundos)));
	}

	/** Deja guardadas alertas del reloj recibidas hace tantos minutos, como si hubieran llegado antes. */
	private void alertasGuardadas(int cantidad, int minutosAtras) {
		for (int i = 0; i < cantidad; i++) {
			jdbc.sql("""
					INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
						fecha_hora_recepcion)
					VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', 'CAIDA', 'MEDIA', :recepcion)""")
					.param("a", asignacion)
					.param("recepcion", Timestamp.from(ahora.minus(minutosAtras, ChronoUnit.MINUTES)))
					.update();
		}
	}

	/** @return los códigos de discrepancia guardados para esa alerta */
	private List<String> discrepancias(UUID eventId) {
		return jdbc.sql("""
				SELECT d.codigo FROM discrepancia_alerta d JOIN alerta a USING (id_alerta)
				WHERE a.event_id = :e ORDER BY d.codigo""").param("e", eventId).query(String.class).list();
	}

	/** @return cuántas alertas técnicas EXCESO_ALERTAS hay */
	private int tecnicas() {
		return jdbc.sql("SELECT count(*) FROM alerta WHERE tipo_evento = 'EXCESO_ALERTAS'").query(Integer.class)
				.single();
	}

	/** @return cuántas alertas envió el reloj del paciente */
	private int alertasDelReloj() {
		return jdbc.sql("SELECT count(*) FROM alerta WHERE origen = 'RELOJ' AND id_asignacion = :a")
				.param("a", asignacion).query(Integer.class).single();
	}

	/** @return la versión que el servidor entrega ahora al reloj del paciente */
	private String versionVigente() throws Exception {
		return mvc.perform(get("/api/reloj/v1/configuracion").header("Authorization", "Bearer " + TOKEN))
				.andReturn().getResponse().getHeader("ETag").replace("\"", "");
	}

	/** @return un paciente con consentimiento y su RUN cifrado de verdad */
	private UUID paciente(String run, String hash) {
		return jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, :hash, CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar(run, ColumnaCifrada.PACIENTE_RUT))
				.param("hash", hash)
				.query(UUID.class).single();
	}

	/** @return la asignación de un reloj nuevo a ese paciente, desde hace un día, con ese token */
	private UUID reloj(String identificador, UUID idPaciente, String token) {
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:id, 'OPERATIVO')
				RETURNING id_dispositivo""")
				.param("id", identificador).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:reloj, :paciente, :inicio, :hash) RETURNING id_asignacion""")
				.param("reloj", reloj)
				.param("paciente", idPaciente)
				.param("inicio", Timestamp.from(ahora.minus(1, ChronoUnit.DAYS)))
				.param("hash", secretos.hash(token))
				.query(UUID.class).single();
	}

}
