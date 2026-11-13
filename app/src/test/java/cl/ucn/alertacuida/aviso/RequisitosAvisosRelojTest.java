package cl.ucn.alertacuida.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.avisoreloj.EnviarAvisosReloj;
import cl.ucn.alertacuida.application.avisoreloj.error.TokenRelojRechazado;
import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.modelo.TipoAvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.BandejaAvisosReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.CanalAvisoReloj;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Avisos al reloj contra la base real, con un canal doble que recuerda lo que se habría enviado: no sale
 * nada hacia Firebase. Cada caso sale de un requisito escrito (cronograma, contrato de docs/api-reloj.yaml,
 * notas del modelo o decisiones del 11-11-2026). La hora del envío se fija para probar los plazos. Sin
 * Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Cada aviso lleva solo identificadores (esquema AvisoFcm), y va al reloj de la alerta.</li>
 * <li>R2 Sin acuse se reintenta a 1, 1 y 2 minutos del envío anterior; a la hora justa ya toca. Después
 * de esos tres reintentos no se envía más.</li>
 * <li>R3 Un aviso acusado no se vuelve a enviar.</li>
 * <li>R4 Un reloj sin token no gasta reintentos: el aviso sale cuando registra su token.</li>
 * <li>R5 Si Firebase falla, el aviso sigue pendiente, no cuenta como enviado y no detiene a los demás.
 * Si rechaza el token, el reloj vuelve a estar sin token, salvo que ya haya registrado otro.</li>
 * <li>R6 Acuse (contrato): 204, repetirlo no cambia la hora, 404 si no es de ese reloj, 400 sin hora.</li>
 * <li>R7 La línea de tiempo muestra el envío y la recepción; con los reintentos agotados y pasada la
 * última espera, muestra que el reloj no lo confirmó.</li>
 * <li>R8 Cambiar la lista de contactos avisa CONFIGURACION_NUEVA al reloj vigente, sin guardar nada; si
 * no hay token o el envío falla, el cambio se guarda igual. Los umbrales no avisan: llegan al reloj en
 * su siguiente sincronización.</li>
 * </ul>
 */
@SpringBootTest(properties = "alertacuida.fcm.intervalo=PT1H")
@AutoConfigureMockMvc
@Import({PostgresDePrueba.class, RequisitosAvisosRelojTest.CanalDePrueba.class})
@Testcontainers(disabledWithoutDocker = true)
class RequisitosAvisosRelojTest {

	private static final Instant T0 = Instant.parse("2026-11-09T15:00:00Z");
	private static final PoliticaReintentos POLITICA = new PoliticaReintentos(
			List.of(Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(2)));
	private static final String TOKEN_RELOJ = "token-reloj-avisos";
	private static final String TOKEN_OTRO = "token-otro-reloj-avisos";
	private static final String FIREBASE = "token-firebase-1";
	private static final String RUN = "11.111.111-1";

	/** Hace de Firebase: recuerda los avisos y puede fallar a pedido. */
	@TestConfiguration
	static class CanalDePrueba {

		/** @return el doble, en lugar del canal configurado */
		@Bean
		@Primary
		CanalDoble canalDoble() {
			return new CanalDoble();
		}

	}

	/** Canal que guarda lo enviado. */
	static final class CanalDoble implements CanalAvisoReloj {

		final List<AvisoReloj> enviados = new CopyOnWriteArrayList<>();
		final List<String> tokensQueFallan = new CopyOnWriteArrayList<>();
		final List<String> tokensRechazados = new CopyOnWriteArrayList<>();

		@Override
		public void enviar(AvisoReloj aviso) {
			if (tokensQueFallan.contains(aviso.token())) {
				throw new IllegalStateException("Firebase no responde");
			}
			if (tokensRechazados.contains(aviso.token())) {
				throw new TokenRelojRechazado(new IllegalArgumentException("UNREGISTERED"));
			}
			enviados.add(aviso);
		}

		/** Olvida lo anterior, para partir cada prueba desde cero. */
		void limpiar() {
			enviados.clear();
			tokensQueFallan.clear();
			tokensRechazados.clear();
		}

	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;
	@Autowired
	private SecretosUnSoloUso secretos;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private BandejaAvisosReloj bandeja;
	@Autowired
	private CanalDoble canal;

	private Instant ahora;
	private UUID paciente;
	private UUID asignacion;
	private UUID dispositivo;
	private UUID alerta;
	private UUID eventId;
	private String coordinadora;

	/** Base vacía; una paciente con su reloj (con token de Firebase) y una alerta; otro reloj ajeno. */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "contacto",
				"registro_auditoria", "usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		canal.limpiar();
		ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		paciente = paciente("12345678-5", "hash-avisos-1");
		asignacion = vincular("R-AV-1", paciente, TOKEN_RELOJ);
		dispositivo = jdbc.sql("SELECT id_dispositivo FROM asignacion_reloj WHERE id_asignacion = :a")
				.param("a", asignacion).query(UUID.class).single();
		tokenFirebase(FIREBASE);
		vincular("R-AV-2", paciente("11111111-1", "hash-avisos-2"), TOKEN_OTRO);
		eventId = UUID.randomUUID();
		alerta = jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					fecha_hora_recepcion)
				VALUES (:e, :a, 'h', 'RELOJ', 'CAIDA', 'GRAVE', :r) RETURNING id_alerta""")
				.param("e", eventId).param("a", asignacion)
				.param("r", Timestamp.from(ahora.minus(1, ChronoUnit.HOURS))).query(UUID.class).single();
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES ('COORDINADOR', 'cifrado', :hash, 'Camila', 'camila@muni.cl', :clave, true)""")
				.param("hash", hashRun.de(Run.de(RUN))).param("clave", encoder.encode("Clave-de-Camila1")).update();
		coordinadora = tokenSesion();
	}

	// ---------- R1: contenido ----------

	/** R1: un aviso de alerta resuelta lleva tipo, idAviso y eventIdAlerta, y nada más. */
	@Test
	void elAvisoLlevaSoloIdentificadores() {
		UUID aviso = aviso("ALERTA_RESUELTA");

		pasada(T0);

		assertThat(canal.enviados).hasSize(1);
		AvisoReloj enviado = canal.enviados.getFirst();
		assertThat(enviado.token()).isEqualTo(FIREBASE);
		assertThat(enviado.datos()).isEqualTo(Map.of("tipo", "ALERTA_RESUELTA", "idAviso", aviso.toString(),
				"eventIdAlerta", eventId.toString()));
		assertThat(fila(aviso)).isEqualTo(new Fila(T0, 1));
	}

	/** R1: en una alerta confirmada viaja quién confirmó: el primero que respondió "Voy en camino". */
	@Test
	void laConfirmacionLlevaAlContactoQueConfirmo() {
		UUID primero = contactoConRespuesta("Hija", "hija@correo.cl", 1, "VOY_EN_CAMINO", T0.minusSeconds(60));
		contactoConRespuesta("Vecino", "vecino@correo.cl", 2, "VOY_EN_CAMINO", T0.minusSeconds(30));
		contactoConRespuesta("Otra", "otra@correo.cl", 3, "NO_PUEDO", T0.minusSeconds(90));
		aviso("ALERTA_CONFIRMADA");

		pasada(T0);

		assertThat(canal.enviados.getFirst().datos()).containsEntry("idContacto", primero.toString());
	}

	/** R1: fuera de la confirmación no viaja ningún contacto, aunque alguien haya confirmado. */
	@Test
	void unaResueltaNoLlevaContacto() {
		contactoConRespuesta("Hija", "hija@correo.cl", 1, "VOY_EN_CAMINO", T0.minusSeconds(60));
		aviso("ALERTA_RESUELTA");

		pasada(T0);

		assertThat(canal.enviados.getFirst().datos()).doesNotContainKey("idContacto");
	}

	// ---------- R2 y R3: reintentos y acuse ----------

	/** R2: los plazos se cuentan desde el envío anterior, y a la hora justa ya toca. */
	@Test
	void reintentaSegunLosPlazos() {
		UUID aviso = aviso("ALERTA_RESUELTA");

		pasada(T0);
		pasada(T0.plusSeconds(59));
		assertThat(canal.enviados).hasSize(1);

		pasada(T0.plusSeconds(60));
		assertThat(canal.enviados).hasSize(2);
		pasada(T0.plusSeconds(119));
		assertThat(canal.enviados).hasSize(2);

		pasada(T0.plusSeconds(120));
		assertThat(canal.enviados).hasSize(3);
		pasada(T0.plusSeconds(239));
		assertThat(canal.enviados).hasSize(3);

		pasada(T0.plusSeconds(240));
		assertThat(canal.enviados).hasSize(4);
		assertThat(fila(aviso)).isEqualTo(new Fila(T0.plusSeconds(240), 4));
	}

	/** R2: tras el tercer reintento no se envía más, por mucho tiempo que pase. */
	@Test
	void noReintentaMasDeTresVeces() {
		UUID aviso = aviso("ALERTA_RESUELTA");
		pasada(T0);
		pasada(T0.plusSeconds(60));
		pasada(T0.plusSeconds(120));
		pasada(T0.plusSeconds(240));

		pasada(T0.plus(1, ChronoUnit.DAYS));

		assertThat(canal.enviados).hasSize(4);
		assertThat(fila(aviso).intentos()).isEqualTo(4);
	}

	/** R3: una vez acusado, no se reintenta. */
	@Test
	void unAvisoAcusadoNoSeReintenta() throws Exception {
		UUID aviso = aviso("ALERTA_RESUELTA");
		pasada(T0);

		acuse(TOKEN_RELOJ, aviso, ahora).andExpect(status().isNoContent());
		pasada(T0.plus(1, ChronoUnit.HOURS));

		assertThat(canal.enviados).hasSize(1);
	}

	// ---------- R4 y R5: sin token y fallas ----------

	/** R4: sin token no se envía ni se gastan reintentos; al registrarlo por la API, sale. */
	@Test
	void sinTokenEsperaYSaleAlRegistrarlo() throws Exception {
		tokenFirebase(null);
		UUID aviso = aviso("ALERTA_RESUELTA");

		pasada(T0);
		pasada(T0.plus(1, ChronoUnit.DAYS));

		assertThat(canal.enviados).isEmpty();
		assertThat(fila(aviso)).isEqualTo(new Fila(null, 0));

		mvc.perform(put("/api/reloj/v1/token-fcm").header("Authorization", "Bearer " + TOKEN_RELOJ)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"token-nuevo\"}"))
				.andExpect(status().isNoContent());
		pasada(T0.plus(2, ChronoUnit.DAYS));

		assertThat(canal.enviados).extracting(AvisoReloj::token).containsExactly("token-nuevo");
	}

	/** R5: si Firebase falla no cuenta como envío; en la siguiente pasada sale. */
	@Test
	void unaFallaDeFirebaseNoGastaIntentos() {
		UUID aviso = aviso("ALERTA_RESUELTA");
		canal.tokensQueFallan.add(FIREBASE);

		pasada(T0);
		pasada(T0.plusSeconds(15));
		assertThat(fila(aviso)).isEqualTo(new Fila(null, 0));

		canal.tokensQueFallan.clear();
		pasada(T0.plusSeconds(30));

		assertThat(canal.enviados).hasSize(1);
		assertThat(fila(aviso)).isEqualTo(new Fila(T0.plusSeconds(30), 1));
	}

	/** R5: el aviso que falla no detiene a los demás. */
	@Test
	void unaFallaNoDetieneALosDemas() {
		aviso("ALERTA_RESUELTA");
		UUID otraAlerta = alertaDeOtroReloj("token-que-falla");
		jdbc.sql("INSERT INTO aviso_fcm (id_alerta, tipo) VALUES (:a, 'ALERTA_RESUELTA')").param("a", otraAlerta)
				.update();
		canal.tokensQueFallan.add("token-que-falla");

		pasada(T0);

		assertThat(canal.enviados).extracting(AvisoReloj::token).containsExactly(FIREBASE);
	}

	/** R5: un token rechazado se borra y el aviso espera al nuevo, sin gastar reintentos. */
	@Test
	void unTokenRechazadoSeBorra() {
		UUID aviso = aviso("ALERTA_RESUELTA");
		canal.tokensRechazados.add(FIREBASE);

		pasada(T0);

		assertThat(tokenGuardado()).isNull();
		assertThat(fila(aviso)).isEqualTo(new Fila(null, 0));
	}

	/** R5: si el reloj ya registró otro token, el rechazo del anterior no lo borra. */
	@Test
	void noSeBorraUnTokenMasNuevo() {
		bandeja.olvidarToken(dispositivo, "token-viejo");

		assertThat(tokenGuardado()).isEqualTo(FIREBASE);
	}

	// ---------- R6: acuse ----------

	/** R6: el acuse se guarda; repetirlo responde 204 y conserva la primera hora. */
	@Test
	void elAcuseEsRepetibleYConservaLaHora() throws Exception {
		UUID aviso = aviso("ALERTA_RESUELTA");
		Instant primera = ahora.minusSeconds(5);

		acuse(TOKEN_RELOJ, aviso, primera).andExpect(status().isNoContent());
		acuse(TOKEN_RELOJ, aviso, ahora).andExpect(status().isNoContent());

		assertThat(jdbc.sql("SELECT fecha_hora_recepcion FROM aviso_fcm WHERE id_aviso = :a").param("a", aviso)
				.query(Timestamp.class).single().toInstant()).isEqualTo(primera);
	}

	/** R6: un aviso de otro reloj o desconocido responde 404 y no cambia nada. */
	@Test
	void elAcuseDeOtroRelojEs404() throws Exception {
		UUID aviso = aviso("ALERTA_RESUELTA");

		acuse(TOKEN_OTRO, aviso, ahora).andExpect(status().isNotFound());
		acuse(TOKEN_RELOJ, UUID.randomUUID(), ahora).andExpect(status().isNotFound());

		assertThat(jdbc.sql("SELECT count(*) FROM aviso_fcm WHERE fecha_hora_recepcion IS NOT NULL")
				.query(Integer.class).single()).isZero();
	}

	/** R6: sin fecha el mensaje está mal formado, y sin token del reloj responde 401. */
	@Test
	void elAcuseExigeHoraYToken() throws Exception {
		UUID aviso = aviso("ALERTA_RESUELTA");

		mvc.perform(post("/api/reloj/v1/avisos-fcm/" + aviso + "/recepcion")
				.header("Authorization", "Bearer " + TOKEN_RELOJ).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/reloj/v1/avisos-fcm/" + aviso + "/recepcion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaHoraRecepcion\": \"" + ahora + "\"}")).andExpect(status().isUnauthorized());
	}

	// ---------- R7: línea de tiempo ----------

	/** R7: se ven el envío y la recepción, y un aviso acusado no aparece como no confirmado. */
	@Test
	void laLineaDeTiempoMuestraEnvioYRecepcion() throws Exception {
		avisoEnviado(4, ahora.minus(10, ChronoUnit.MINUTES), ahora.minus(9, ChronoUnit.MINUTES));

		detalle().andExpect(jsonPath("$.lineaTiempo[1].tipo").value("FCM_ENVIADO"))
				.andExpect(jsonPath("$.lineaTiempo[2].tipo").value("FCM_RECIBIDO"))
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", not(hasItem("FCM_NO_CONFIRMADO"))));
	}

	/** R7: agotados los reintentos y pasada la última espera, queda a la vista que no lo confirmó. */
	@Test
	void conLosReintentosAgotadosQuedaNoConfirmado() throws Exception {
		avisoEnviado(4, ahora.minus(3, ChronoUnit.MINUTES), null);

		detalle().andExpect(jsonPath("$.lineaTiempo[2].tipo").value("FCM_NO_CONFIRMADO"))
				.andExpect(jsonPath("$.lineaTiempo[2].detalle").value("ALERTA_RESUELTA"));
	}

	/** R7: recién enviado el último reintento, el reloj todavía puede acusarlo. */
	@Test
	void elUltimoReintentoAunPuedeAcusarse() throws Exception {
		avisoEnviado(4, ahora.minusSeconds(30), null);

		detalle().andExpect(jsonPath("$.lineaTiempo[*].tipo", not(hasItem("FCM_NO_CONFIRMADO"))));
	}

	/** R7: mientras queden reintentos, no se muestra como no confirmado aunque haya pasado tiempo. */
	@Test
	void conReintentosPendientesNoEsNoConfirmado() throws Exception {
		avisoEnviado(3, ahora.minus(1, ChronoUnit.HOURS), null);

		detalle().andExpect(jsonPath("$.lineaTiempo[*].tipo", not(hasItem("FCM_NO_CONFIRMADO"))));
	}

	// ---------- R8: configuración nueva ----------

	/** R8: agregar un contacto avisa al reloj vigente solo con el tipo, y no guarda ningún aviso. */
	@Test
	void agregarUnContactoAvisaConfiguracionNueva() throws Exception {
		agregarContacto("Hija", "hija@correo.cl").andExpect(status().isCreated());

		assertThat(canal.enviados).hasSize(1);
		assertThat(canal.enviados.getFirst().token()).isEqualTo(FIREBASE);
		assertThat(canal.enviados.getFirst().datos()).isEqualTo(Map.of("tipo", "CONFIGURACION_NUEVA"));
		assertThat(jdbc.sql("SELECT count(*) FROM aviso_fcm").query(Integer.class).single()).isZero();
	}

	/** R8: editar, ordenar, registrar el opt-in, desactivar y reactivar también avisan. */
	@Test
	void cadaCambioDeLaListaAvisa() throws Exception {
		String uno = id(agregarContactoConWhatsapp("Hija", "+56912345678", "hija@correo.cl"));
		String dos = id(agregarContacto("Vecino", "vecino@correo.cl"));
		canal.limpiar();
		String ruta = "/api/pacientes/" + paciente + "/contactos/";

		conJson(put(ruta + uno), contacto("Hija Ana", "\"+56912345678\"", "hija@correo.cl"))
				.andExpect(status().isNoContent());
		conJson(put(ruta + "orden"), "{\"orden\": [\"" + dos + "\", \"" + uno + "\"]}")
				.andExpect(status().isNoContent());
		conJson(put(ruta + uno + "/opt-in"), "{\"aceptado\": true}").andExpect(status().isNoContent());
		mvc.perform(conToken(post(ruta + uno + "/desactivar"))).andExpect(status().isNoContent());
		mvc.perform(conToken(post(ruta + uno + "/reactivar"))).andExpect(status().isNoContent());

		assertThat(canal.enviados).hasSize(5).extracting(AvisoReloj::tipo)
				.containsOnly(TipoAvisoReloj.CONFIGURACION_NUEVA);
	}

	/** R8: lo que no cambia nada no avisa: reactivar a quien ya está activo. */
	@Test
	void sinCambioNoAvisa() throws Exception {
		String uno = id(agregarContacto("Hija", "hija@correo.cl"));
		canal.limpiar();

		mvc.perform(conToken(post("/api/pacientes/" + paciente + "/contactos/" + uno + "/reactivar")))
				.andExpect(status().isNoContent());

		assertThat(canal.enviados).isEmpty();
	}

	/** R8: sin token no se avisa, y el contacto se guarda igual. */
	@Test
	void sinTokenElContactoSeGuardaIgual() throws Exception {
		tokenFirebase(null);

		agregarContacto("Hija", "hija@correo.cl").andExpect(status().isCreated());

		assertThat(canal.enviados).isEmpty();
	}

	/** R8: si Firebase falla, el contacto se guarda igual: el reloj lo sabrá en su próxima sincronización. */
	@Test
	void unaFallaNoImpideGuardarElContacto() throws Exception {
		canal.tokensQueFallan.add(FIREBASE);

		agregarContacto("Hija", "hija@correo.cl").andExpect(status().isCreated());

		assertThat(jdbc.sql("SELECT count(*) FROM contacto_paciente WHERE id_paciente = :p").param("p", paciente)
				.query(Integer.class).single()).isEqualTo(1);
	}

	/** R8: un reloj cuyo vínculo terminó ya no es el del paciente: no se le avisa. */
	@Test
	void noSeAvisaAUnRelojDesvinculado() throws Exception {
		jdbc.sql("UPDATE asignacion_reloj SET fecha_fin = :f WHERE id_asignacion = :a")
				.param("f", Timestamp.from(ahora)).param("a", asignacion).update();

		agregarContacto("Hija", "hija@correo.cl").andExpect(status().isCreated());

		assertThat(canal.enviados).isEmpty();
	}

	/** R8: cambiar un umbral no avisa: llega en la siguiente sincronización. */
	@Test
	void cambiarUnUmbralNoAvisa() throws Exception {
		String perfil = "/api/pacientes/" + paciente + "/perfil-clinico/umbrales/SPO2";

		conJson(put(perfil), "{\"graveBajo\": 85, \"normalMin\": 90}").andExpect(status().isNoContent());
		mvc.perform(conToken(delete(perfil))).andExpect(status().isNoContent());

		assertThat(canal.enviados).isEmpty();
	}

	// ---------- Apoyo ----------

	/**
	 * Lo guardado de un aviso.
	 *
	 * @param envio    último envío, o null
	 * @param intentos envíos hechos
	 */
	private record Fila(Instant envio, int intentos) {
	}

	/** Corre una pasada del envío a esa hora. */
	private void pasada(Instant hora) {
		new EnviarAvisosReloj(bandeja, canal, POLITICA, Clock.fixed(hora, ZoneOffset.UTC)).enviar();
	}

	/** @return un aviso pendiente de la alerta, de ese tipo */
	private UUID aviso(String tipo) {
		return jdbc.sql("INSERT INTO aviso_fcm (id_alerta, tipo) VALUES (:a, :t) RETURNING id_aviso")
				.param("a", alerta).param("t", tipo).query(UUID.class).single();
	}

	/** Deja un aviso ALERTA_RESUELTA ya enviado esa cantidad de veces. */
	private void avisoEnviado(int intentos, Instant envio, Instant recepcion) {
		jdbc.sql("""
				INSERT INTO aviso_fcm (id_alerta, tipo, fecha_hora_envio, fecha_hora_recepcion, intentos)
				VALUES (:a, 'ALERTA_RESUELTA', :e, :r, :i)""")
				.param("a", alerta).param("e", Timestamp.from(envio))
				.param("r", recepcion == null ? null : Timestamp.from(recepcion)).param("i", intentos).update();
	}

	/** @return lo guardado de ese aviso */
	private Fila fila(UUID aviso) {
		return jdbc.sql("SELECT fecha_hora_envio, intentos FROM aviso_fcm WHERE id_aviso = :a").param("a", aviso)
				.query((f, n) -> new Fila(f.getTimestamp(1) == null ? null : f.getTimestamp(1).toInstant(),
						f.getInt(2)))
				.single();
	}

	/** @return el vínculo de un contacto nuevo que ya recibió el aviso y respondió a esa hora */
	private UUID contactoConRespuesta(String nombre, String correo, int prioridad, String respuesta, Instant hora) {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, correo, correo_hash) VALUES (:n, :c, :h) RETURNING id_contacto""")
				.param("n", cifrado.cifrar(nombre, ColumnaCifrada.CONTACTO_NOMBRE))
				.param("c", cifrado.cifrar(correo, ColumnaCifrada.CONTACTO_CORREO))
				.param("h", "hash-" + correo).query(UUID.class).single();
		UUID vinculo = jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad)
				VALUES (:p, :c, 'VECINO', :o) RETURNING id_contacto_paciente""")
				.param("p", paciente).param("c", persona).param("o", prioridad).query(UUID.class).single();
		jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda, fecha_hora_envio,
					respuesta, fecha_hora_respuesta)
				VALUES (gen_random_uuid(), :a, :c, 'CORREO', 1, :h, :r, :h)""")
				.param("a", alerta).param("c", vinculo).param("h", Timestamp.from(hora)).param("r", respuesta).update();
		return vinculo;
	}

	/** @return una alerta de otro reloj, que tiene ese token de Firebase */
	private UUID alertaDeOtroReloj(String token) {
		UUID otraAsignacion = jdbc.sql("""
				UPDATE smartwatch SET token_fcm = :t WHERE identificador_reloj = 'R-AV-2' RETURNING id_dispositivo""")
				.param("t", token).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					fecha_hora_recepcion)
				SELECT gen_random_uuid(), id_asignacion, 'h', 'RELOJ', 'FC', 'MEDIA', now() FROM asignacion_reloj
				WHERE id_dispositivo = :d RETURNING id_alerta""")
				.param("d", otraAsignacion).query(UUID.class).single();
	}

	/** Guarda ese token de Firebase en el reloj de la paciente. */
	private void tokenFirebase(String token) {
		jdbc.sql("UPDATE smartwatch SET token_fcm = :t WHERE id_dispositivo = :d").param("t", token)
				.param("d", dispositivo).update();
	}

	/** @return el token de Firebase guardado para el reloj de la paciente */
	private String tokenGuardado() {
		return jdbc.sql("SELECT token_fcm FROM smartwatch WHERE id_dispositivo = :d").param("d", dispositivo)
				.query((f, n) -> f.getString(1)).list().getFirst();
	}

	/** El reloj acusa un aviso. */
	private ResultActions acuse(String token, UUID aviso, Instant fecha) throws Exception {
		return mvc.perform(post("/api/reloj/v1/avisos-fcm/" + aviso + "/recepcion")
				.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaHoraRecepcion\": \"" + fecha + "\"}"));
	}

	/** Pide el detalle de la alerta. */
	private ResultActions detalle() throws Exception {
		return mvc.perform(conToken(get("/api/alertas/" + alerta)));
	}

	/** Agrega un contacto solo con correo a la paciente, por la API. */
	private ResultActions agregarContacto(String nombre, String correo) throws Exception {
		return conJson(post("/api/pacientes/" + paciente + "/contactos"), contacto(nombre, "null", correo));
	}

	/** Agrega un contacto con WhatsApp y correo a la paciente, por la API. */
	private ResultActions agregarContactoConWhatsapp(String nombre, String whatsapp, String correo) throws Exception {
		return conJson(post("/api/pacientes/" + paciente + "/contactos"),
				contacto(nombre, "\"" + whatsapp + "\"", correo));
	}

	/**
	 * @param whatsapp valor JSON tal cual: con comillas, o null
	 * @return el cuerpo JSON de un contacto disponible todo el día
	 */
	private static String contacto(String nombre, String whatsapp, String correo) {
		return """
				{"nombre": "%s", "whatsapp": %s, "correo": "%s", "tipo": "VECINO", "cuidadorPrincipal": false,
				"horaInicio": null, "horaFin": null}""".formatted(nombre, whatsapp, correo);
	}

	/** @return el "id" de la respuesta de un alta */
	private static String id(ResultActions respuesta) throws Exception {
		return JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
	}

	/** Petición de la coordinadora con cuerpo JSON. */
	private ResultActions conJson(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
		return mvc.perform(conToken(peticion).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** @return la petición con el token de la coordinadora */
	private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
		return peticion.header("Authorization", "Bearer " + coordinadora);
	}

	/** Inicia sesión y devuelve el token de acceso. */
	private String tokenSesion() throws Exception {
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + RUN + "\", \"contrasena\": \"Clave-de-Camila1\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.token");
	}

	/** @return una paciente con consentimiento y su RUN cifrado */
	private UUID paciente(String run, String hash) {
		return jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, :hash, CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar(run, ColumnaCifrada.PACIENTE_RUT))
				.param("hash", hash).query(UUID.class).single();
	}

	/** @return la asignación de un reloj nuevo a esa paciente, con ese token */
	private UUID vincular(String identificador, UUID idPaciente, String token) {
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:id, 'OPERATIVO')
				RETURNING id_dispositivo""").param("id", identificador).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:reloj, :paciente, :inicio, :hash) RETURNING id_asignacion""")
				.param("reloj", reloj).param("paciente", idPaciente)
				.param("inicio", Timestamp.from(ahora.minus(1, ChronoUnit.DAYS)))
				.param("hash", secretos.hash(token)).query(UUID.class).single();
	}

}
