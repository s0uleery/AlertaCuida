package cl.ucn.alertacuida.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.RecordarLlegada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.BaseDePrueba;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
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
 * Pasos de la oficina PRLAC en la página del enlace del correo, iguales a los mensajes de WhatsApp N1-02 a
 * N1-11 (decisiones del 26-11-2026). Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 El enlace de un coordinador abre los pasos de la oficina; el de un contacto del paciente, no.</li>
 * <li>R2 "Tomo la alerta" deja al coordinador a cargo, pero el reloj sigue avisando (la alerta no se
 * confirma). Los demás coordinadores ven quién la tiene y no pueden avanzar.</li>
 * <li>R3 "Paciente está bien" confirma la alerta (el reloj deja de avisar) y pide cerrar con "Sí ocurrió" o
 * "Falsa alarma"; cada uno cierra con su clasificación y avisa al reloj.</li>
 * <li>R4 "Necesita atención" o "No contesta" piden coordinar la visita: "Personal asignado" confirma la alerta;
 * "Sin disponibilidad" no, y el reloj sigue avisando.</li>
 * <li>R5 "Ya llegó" no resuelve la alerta: pide el resultado de la visita. "Apoyo no urgente" la cierra como
 * POSITIVA (queda en seguimiento); "Emergencia" pide el desenlace, y "Trasladada" deriva a SAMU.</li>
 * <li>R6 Un paso que no corresponde a la etapa actual responde 409 y no cambia nada.</li>
 * <li>R7 Cerrada la alerta, el responsable puede agregar un comentario al cierre.</li>
 * <li>R8 A los 30 minutos de "Personal asignado" sin llegada, sale un correo recordatorio al responsable, una
 * sola vez; su enlace abre la misma etapa.</li>
 * <li>R9 En la página del contacto, después de llamar, el resultado (contestó o no) queda en su aviso.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosPasosOficinaTest {

	private static final Instant DETECCION = Instant.parse("2026-11-26T15:00:00Z");

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private FirmaRespuesta firma;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;
	@Autowired
	private RecordarLlegada recordarLlegada;

	private UUID paciente;
	private UUID alerta;
	private UUID coordinadora;
	private String token;
	private String tokenOtro;

	/** Base vacía; Rosa con su reloj, una caída media y el aviso por correo a dos coordinadores. */
	@BeforeEach
	void preparar() {
		BaseDePrueba.vaciar(jdbc);
		paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-oficina', 'Rosa', CURRENT_DATE - 10, CURRENT_DATE - 10) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, numero_sim, estado)
				VALUES ('R-OFICINA', '+56987654321', 'OPERATIVO') RETURNING id_dispositivo""")
				.query(UUID.class).single();
		UUID asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, 'hash-token-oficina') RETURNING id_asignacion""")
				.param("r", reloj).param("p", paciente).param("inicio", Timestamp.from(DETECCION.minusSeconds(86_400)))
				.query(UUID.class).single();
		alerta = jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', 'CAIDA', 'MEDIA', :t, :t) RETURNING id_alerta""")
				.param("a", asignacion).param("t", Timestamp.from(DETECCION)).query(UUID.class).single();
		coordinadora = cuenta("COORDINADOR", "22.222.222-2", "Ana Coordinadora");
		UUID otra = cuenta("COORDINADOR", "33.333.333-3", "Berta Coordinadora");
		token = firma.firmar(avisoOficina(coordinadora, "AVISO"));
		tokenOtro = firma.firmar(avisoOficina(otra, "AVISO"));
	}

	// ---------- R1 ----------

	/** R1: el enlace del coordinador trae los pasos de la oficina, empezando por tomar la alerta. */
	@Test
	void elEnlaceDeUnCoordinadorAbreLosPasosDeLaOficina() throws Exception {
		consultar(token).andExpect(status().isOk())
				.andExpect(jsonPath("$.oficina.etapa").value("SIN_TOMAR"))
				.andExpect(jsonPath("$.oficina.idAlerta").value(alerta.toString()));
	}

	/** R1: el enlace de un contacto del paciente no trae pasos de la oficina, y no puede darlos. */
	@Test
	void elEnlaceDeUnContactoNoTienePasosDeLaOficina() throws Exception {
		String deContacto = firma.firmar(correoAContacto());

		consultar(deContacto).andExpect(jsonPath("$.oficina").value(nullValue()));
		paso(deContacto, "TOMAR").andExpect(status().isConflict());
	}

	// ---------- R2 ----------

	/** R2: tomar deja a cargo, sin confirmar: el reloj sigue avisando. */
	@Test
	void tomarLaAlertaNoDetieneAlReloj() throws Exception {
		paso(token, "TOMAR").andExpect(status().isOk()).andExpect(jsonPath("$.oficina.etapa").value("LLAMAR"))
				.andExpect(jsonPath("$.oficina.esResponsable").value(true));

		assertThat(estado("estado_atencion")).isEqualTo("EN_ATENCION");
		assertThat(estado("estado_contacto")).isNotEqualTo("CONFIRMADA");
		assertThat(avisosAlReloj("ALERTA_CONFIRMADA")).isZero();
	}

	/** R2: la otra coordinadora ve quién la tiene y no puede avanzar. */
	@Test
	void laOtraCoordinadoraVeQuienLaTiene() throws Exception {
		paso(token, "TOMAR");

		consultar(tokenOtro).andExpect(jsonPath("$.oficina.etapa").value("A_CARGO_DE_OTRO"))
				.andExpect(jsonPath("$.oficina.responsable").value("Ana Coordinadora"))
				.andExpect(jsonPath("$.oficina.esResponsable").value(false));
		paso(tokenOtro, "TOMAR").andExpect(status().isConflict());
		paso(tokenOtro, "PACIENTE_BIEN").andExpect(status().isConflict());
	}

	// ---------- R3 ----------

	/** R3: "está bien" confirma y pide cerrar; "Falsa alarma" cierra y avisa al reloj. */
	@Test
	void pacienteBienYFalsaAlarmaCierraLaAlerta() throws Exception {
		paso(token, "TOMAR");
		paso(token, "PACIENTE_BIEN").andExpect(jsonPath("$.oficina.etapa").value("CONFIRMAR_EVENTO"));
		assertThat(estado("estado_contacto")).isEqualTo("CONFIRMADA");
		assertThat(avisosAlReloj("ALERTA_CONFIRMADA")).isEqualTo(1);

		paso(token, "FALSA_ALARMA").andExpect(status().isOk()).andExpect(jsonPath("$.oficina.etapa").value("CERRADA"))
				.andExpect(jsonPath("$.oficina.clasificacion").value("FALSA_ALARMA"));

		assertThat(estado("estado_atencion")).isEqualTo("RESUELTA");
		assertThat(clasificacion()).isEqualTo("FALSA_ALARMA");
		assertThat(avisosAlReloj("ALERTA_RESUELTA")).isEqualTo(1);
	}

	/**
	 * R3: "No se pudo confirmar" ya no existe (decisión del 26-11-2026): si la persona está bien, se cierra como
	 * "Sí ocurrió" o "Falsa alarma". El paso se rechaza y la alerta sigue abierta.
	 */
	@Test
	void noSePudoConfirmarYaNoEsUnPaso() throws Exception {
		paso(token, "TOMAR");
		paso(token, "PACIENTE_BIEN");

		paso(token, "NO_CONFIRMADO").andExpect(status().isBadRequest());

		assertThat(estado("estado_atencion")).isEqualTo("EN_ATENCION");
	}

	/** R3: "Sí ocurrió" cierra como POSITIVA y deja el caso en seguimiento. */
	@Test
	void siOcurrioDejaElCasoEnSeguimiento() throws Exception {
		paso(token, "TOMAR");
		paso(token, "PACIENTE_BIEN");

		paso(token, "OCURRIO").andExpect(status().isOk());

		assertThat(clasificacion()).isEqualTo("POSITIVA");
		assertThat(estado("estado_atencion")).isEqualTo("EN_SEGUIMIENTO");
	}

	// ---------- R4 ----------

	/** R4: "Personal asignado" confirma la alerta y espera la llegada. */
	@Test
	void personalAsignadoConfirmaLaAlerta() throws Exception {
		paso(token, "TOMAR");
		paso(token, "NO_CONTESTA").andExpect(jsonPath("$.oficina.etapa").value("COORDINAR_VISITA"));
		assertThat(estado("estado_contacto")).isNotEqualTo("CONFIRMADA");

		paso(token, "PERSONAL_ASIGNADO").andExpect(jsonPath("$.oficina.etapa").value("ESPERANDO_LLEGADA"))
				.andExpect(jsonPath("$.oficina.recordatorio").value(false));

		assertThat(estado("estado_contacto")).isEqualTo("CONFIRMADA");
		assertThat(avisosAlReloj("ALERTA_CONFIRMADA")).isEqualTo(1);
	}

	/** R4: "Sin disponibilidad" no confirma: el reloj sigue y la página lo dice. */
	@Test
	void sinDisponibilidadDejaQueElRelojSiga() throws Exception {
		paso(token, "TOMAR");
		paso(token, "NECESITA_ATENCION");

		paso(token, "SIN_DISPONIBILIDAD").andExpect(status().isOk())
				.andExpect(jsonPath("$.oficina.etapa").value("SIN_DISPONIBILIDAD"));

		assertThat(estado("estado_contacto")).isNotEqualTo("CONFIRMADA");
		assertThat(avisosAlReloj("ALERTA_CONFIRMADA")).isZero();
		assertThat(estado("estado_atencion")).isEqualTo("EN_ATENCION");
	}

	// ---------- R5 ----------

	/** R5: la llegada pide el resultado; "Apoyo no urgente" cierra como POSITIVA, en seguimiento. */
	@Test
	void llegadaYApoyoNoUrgente() throws Exception {
		hastaLaLlegada();
		assertThat(estado("estado_atencion")).isEqualTo("EN_ATENCION");

		paso(token, "APOYO_NO_URGENTE").andExpect(jsonPath("$.oficina.etapa").value("CERRADA"))
				.andExpect(jsonPath("$.oficina.resultadoVisita").value("APOYO_NO_URGENTE"));

		assertThat(clasificacion()).isEqualTo("POSITIVA");
		assertThat(estado("estado_atencion")).isEqualTo("EN_SEGUIMIENTO");
	}

	/** R5: "Persona bien" después de la visita vuelve a preguntar si ocurrió. */
	@Test
	void personaBienDespuesDeLaVisita() throws Exception {
		hastaLaLlegada();

		paso(token, "VISITA_BIEN").andExpect(jsonPath("$.oficina.etapa").value("CONFIRMAR_EVENTO"));
		paso(token, "FALSA_ALARMA").andExpect(status().isOk());

		assertThat(clasificacion()).isEqualTo("FALSA_ALARMA");
	}

	/** R5: emergencia y traslado: POSITIVA con derivación a SAMU, visible en la ficha. */
	@Test
	void emergenciaTrasladadaDerivaASamu() throws Exception {
		hastaLaLlegada();
		paso(token, "EMERGENCIA").andExpect(jsonPath("$.oficina.etapa").value("EMERGENCIA"));

		paso(token, "TRASLADADA").andExpect(jsonPath("$.oficina.etapa").value("CERRADA"));

		assertThat(clasificacion()).isEqualTo("POSITIVA");
		assertThat(jdbc.sql("""
				SELECT count(*) FROM seguimiento
				WHERE id_alerta = :a AND tipo = 'DERIVACION' AND codigo_destino = 'SAMU'""")
				.param("a", alerta).query(Integer.class).single()).isEqualTo(1);
		ficha().andExpect(jsonPath("$.lineaTiempo[*].tipo", hasItem("PERSONAL_ASIGNADO")))
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", hasItem("LLEGADA_PERSONAL")))
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", hasItem("RESULTADO_VISITA")))
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", hasItem("LLAMADA_OFICINA")));
	}

	// ---------- R6 ----------

	/** R6: pasos fuera de orden no cambian nada. */
	@Test
	void unPasoFueraDeOrdenSeRechaza() throws Exception {
		paso(token, "PACIENTE_BIEN").andExpect(status().isConflict());
		paso(token, "TOMAR");
		paso(token, "LLEGO").andExpect(status().isConflict());
		paso(token, "FALSA_ALARMA").andExpect(status().isConflict());

		consultar(token).andExpect(jsonPath("$.oficina.etapa").value("LLAMAR"));
		assertThat(estado("estado_atencion")).isEqualTo("EN_ATENCION");
	}

	// ---------- R7 ----------

	/** R7: el comentario se agrega a las observaciones del cierre; antes del cierre, no. */
	@Test
	void comentarioDespuesDelCierre() throws Exception {
		paso(token, "TOMAR");
		comentar(token, "Antes de tiempo").andExpect(status().isConflict());
		paso(token, "PACIENTE_BIEN");
		paso(token, "FALSA_ALARMA");

		comentar(token, "La vecina la acompañó").andExpect(status().isOk());
		comentar(tokenOtro, "No soy la responsable").andExpect(status().isConflict());

		String observaciones = cifrado.descifrar(jdbc.sql(
				"SELECT observaciones_cierre FROM atencion_alerta WHERE id_alerta = :a")
				.param("a", alerta).query(String.class).single(), ColumnaCifrada.ATENCION_OBSERVACIONES);
		assertThat(observaciones).contains("La vecina la acompañó").doesNotContain("No soy la responsable");
	}

	// ---------- R8 ----------

	/** R8: el recordatorio sale una vez, a los 30 minutos, y su enlace abre la misma etapa con el aviso. */
	@Test
	void recordatorioDeLlegadaUnaSolaVez() throws Exception {
		paso(token, "TOMAR");
		paso(token, "NO_CONTESTA");
		paso(token, "PERSONAL_ASIGNADO");

		recordarLlegada.recordar();
		assertThat(recordatorios()).isZero();

		jdbc.sql("UPDATE atencion_alerta SET fecha_hora_personal_asignado = now() - interval '31 minutes'")
				.update();
		recordarLlegada.recordar();
		recordarLlegada.recordar();

		assertThat(recordatorios()).isEqualTo(1);
		UUID recordatorio = jdbc.sql("""
				SELECT id_intento FROM registro_intento WHERE id_alerta = :a AND proposito = 'RECORDATORIO'""")
				.param("a", alerta).query(UUID.class).single();
		consultar(firma.firmar(recordatorio)).andExpect(jsonPath("$.oficina.etapa").value("ESPERANDO_LLEGADA"))
				.andExpect(jsonPath("$.oficina.recordatorio").value(true));
		paso(token, "SIGUE_EN_CAMINO").andExpect(status().isOk());
		paso(token, "LLEGO").andExpect(jsonPath("$.oficina.etapa").value("RESULTADO_VISITA"));
	}

	// ---------- R9 ----------

	/** R9: el contacto dice si contestaron; queda en su aviso una sola vez. */
	@Test
	void elContactoRegistraSiContestaron() throws Exception {
		UUID aviso = correoAContacto();
		String deContacto = firma.firmar(aviso);

		resultadoLlamada(deContacto, "NO_CONTESTA").andExpect(status().isOk())
				.andExpect(jsonPath("$.resultadoLlamada").value("NO_CONTESTA"));
		resultadoLlamada(deContacto, "CONTESTA").andExpect(status().isOk())
				.andExpect(jsonPath("$.resultadoLlamada").value("NO_CONTESTA"));

		assertThat(jdbc.sql("SELECT resultado_llamada FROM registro_intento WHERE id_intento = :i")
				.param("i", aviso).query(String.class).single()).isEqualTo("NO_CONTESTA");
	}

	// ---------- Ayudas ----------

	/** Toma, no contesta, asigna personal y registra la llegada. */
	private void hastaLaLlegada() throws Exception {
		paso(token, "TOMAR");
		paso(token, "NO_CONTESTA");
		paso(token, "PERSONAL_ASIGNADO");
		paso(token, "LLEGO").andExpect(jsonPath("$.oficina.etapa").value("RESULTADO_VISITA"));
	}

	/** @return la consulta pública de la página del enlace */
	private ResultActions consultar(String token) throws Exception {
		return mvc.perform(post("/api/respuestas-contacto/consulta").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\"}"));
	}

	/** @return el resultado de dar ese paso de la oficina */
	private ResultActions paso(String token, String paso) throws Exception {
		return mvc.perform(post("/api/respuestas-contacto/paso").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\", \"paso\": \"" + paso + "\"}"));
	}

	/** @return el resultado de agregar ese comentario al cierre */
	private ResultActions comentar(String token, String texto) throws Exception {
		return mvc.perform(post("/api/respuestas-contacto/paso").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\", \"paso\": \"COMENTARIO\", \"comentario\": \"" + texto
						+ "\"}"));
	}

	/** @return el resultado de que el contacto diga si contestaron */
	private ResultActions resultadoLlamada(String token, String resultado) throws Exception {
		return mvc.perform(post("/api/respuestas-contacto/resultado-llamada").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\", \"resultado\": \"" + resultado + "\"}"));
	}

	/** @return la ficha de la alerta, vista por un administrador */
	private ResultActions ficha() throws Exception {
		cuenta("ADMINISTRADOR", "11.111.111-1", "Admin");
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"11.111.111-1\", \"contrasena\": \"Clave-de-prueba1\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String sesion = JsonPath.read(cuerpo, "$.token");
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/alertas/" + alerta).header("Authorization", "Bearer " + sesion));
	}

	/** @return esa columna de la vista alerta_estado */
	private String estado(String columna) {
		return jdbc.sql("SELECT " + columna + " FROM alerta_estado WHERE id_alerta = :a").param("a", alerta)
				.query(String.class).single();
	}

	/** @return la clasificación del cierre */
	private String clasificacion() {
		return jdbc.sql("SELECT clasificacion FROM atencion_alerta WHERE id_alerta = :a").param("a", alerta)
				.query(String.class).single();
	}

	/** @return cuántos avisos de ese tipo hay para el reloj */
	private int avisosAlReloj(String tipo) {
		return jdbc.sql("SELECT count(*) FROM aviso_fcm WHERE id_alerta = :a AND tipo = :t")
				.param("a", alerta).param("t", tipo).query(Integer.class).single();
	}

	/** @return cuántos correos recordatorio se encolaron */
	private int recordatorios() {
		return jdbc.sql("SELECT count(*) FROM registro_intento WHERE id_alerta = :a AND proposito = 'RECORDATORIO'")
				.param("a", alerta).query(Integer.class).single();
	}

	/** @return una cuenta activa con ese rol, nombre y la contraseña "Clave-de-prueba1" */
	private UUID cuenta(String rol, String run, String nombre) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :clave, true) RETURNING id_usuario""")
				.param("rol", rol).param("hash", hashRun.de(Run.de(run))).param("nombre", nombre)
				.param("correo", run.replace(".", "") + "@muni.cl")
				.param("clave", encoder.encode("Clave-de-prueba1"))
				.query(UUID.class).single();
	}

	/** @return el correo ya enviado a esa cuenta, como contacto interno, con ese propósito */
	private UUID avisoOficina(UUID usuario, String proposito) {
		UUID contacto = jdbc.sql("INSERT INTO contacto (id_usuario) VALUES (:u) RETURNING id_contacto")
				.param("u", usuario).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_interno, canal, proposito,
				fecha_hora_envio) VALUES (gen_random_uuid(), :a, :c, 'CORREO', :p, :t) RETURNING id_intento""")
				.param("a", alerta).param("c", contacto).param("p", proposito)
				.param("t", Timestamp.from(DETECCION.plusSeconds(40))).query(UUID.class).single();
	}

	/** @return el correo ya enviado a la hija de Rosa, en la ronda 1 */
	private UUID correoAContacto() {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, correo, correo_hash) VALUES (:nombre, :correo, 'hash-c-hija-oficina')
				RETURNING id_contacto""")
				.param("nombre", cifrado.cifrar("Hija", ColumnaCifrada.CONTACTO_NOMBRE))
				.param("correo", cifrado.cifrar("hija@correo.cl", ColumnaCifrada.CONTACTO_CORREO))
				.query(UUID.class).single();
		UUID vinculo = BaseDePrueba.vincular(jdbc, paciente, persona, "OTRO", 1);
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda,
				fecha_hora_envio) VALUES (gen_random_uuid(), :a, :v, 'CORREO', 1, :t) RETURNING id_intento""")
				.param("a", alerta).param("v", vinculo).param("t", Timestamp.from(DETECCION.plusSeconds(40)))
				.query(UUID.class).single();
	}

}
