package cl.ucn.alertacuida.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Atención de alertas vista desde afuera: cada caso sale de un requisito escrito (cronograma, contrato
 * de docs/api-reloj.yaml, notas del modelo de datos o decisiones del 11-11-2026), no del código. Las
 * acciones pasan por la API; la base solo prepara lo que aún no tiene API (los avisos enviados) y revisa
 * lo guardado. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Línea de tiempo: recepción, agravamiento, cada aviso con sus estados de entrega y respuesta,
 * salto de contacto, sin respuesta, avisos FCM, atención y seguimiento, en orden.</li>
 * <li>R2 Resolver exige observaciones en toda alerta, y clasificación solo en las de salud; las
 * técnicas no se clasifican. Las observaciones se guardan cifradas.</li>
 * <li>R3 Puede resolver cualquier cuenta con ALERTAS:EDITAR. Si nadie la había asumido, quien resuelve
 * queda como responsable; si alguien la asumió, sigue siéndolo.</li>
 * <li>R4 Una alerta cancelada, ya resuelta o cerrada por el sistema no se resuelve, y dos personas a la
 * vez no la resuelven dos veces.</li>
 * <li>R5 Resolver una alerta del reloj deja pendiente, una sola vez, el aviso FCM ALERTA_RESUELTA, que
 * la línea de tiempo ya muestra. Las alertas del servidor no lo generan: el reloj no las escala.</li>
 * <li>R6 Una POSITIVA queda EN_SEGUIMIENTO hasta cerrarlo; una FALSA_ALARMA queda RESUELTA.</li>
 * <li>R7 Asumir deja la alerta EN_ATENCION. Si ya se asumió, se resolvió o se canceló, responde 409, y
 * de dos personas a la vez solo una la toma.</li>
 * <li>R8 El reloj informa el fin de la lista (contrato sin-respuesta): 200 también al repetirlo, sin
 * cambiar la hora; 404 si no es de ese reloj; 409 si está cancelada.</li>
 * <li>R9 La lista usa el formato del front; las SIN_RESPUESTA abiertas van primero con prioridad
 * máxima, aunque otra sea más grave o más nueva; después, las más recientes.</li>
 * <li>R10 Ver exige ALERTAS:VER y actuar, ALERTAS:EDITAR. Lecturas y cambios quedan auditados.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosAtencionAlertasTest {

	private static final String TOKEN_RELOJ = "token-reloj-atencion";
	private static final String TOKEN_OTRO_RELOJ = "token-otro-reloj-atencion";
	private static final String OBSERVACION = "Se llamó a la hija y confirmó que está bien";
	private static final String RUN_COORDINADORA = "11.111.111-1";
	private static final String RUN_PERSONAL = "22.222.222-2";
	private static final String RUN_SOLO_VER = "12.345.678-5";
	private static final String RUN_SIN_ALERTAS = "9.876.543-3";

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

	private Instant ahora;
	private UUID paciente;
	private UUID asignacion;
	private UUID coordinadora;
	private UUID personal;
	private String tokenCoordinadora;
	private String tokenPersonal;

	/**
	 * Base vacía; una paciente con su reloj, otro reloj ajeno, una coordinadora y una persona de rol
	 * PERSONAL, que tienen ALERTAS:VER y ALERTAS:EDITAR.
	 */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente", "contacto",
				"registro_auditoria", "usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		jdbc.sql("DELETE FROM rol WHERE codigo IN ('SOLO_VER', 'SIN_ALERTAS')").update();
		ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		paciente = paciente("12345678-5", "hash-atencion-1", "María", "Soto Rojas", LocalDate.of(1940, 3, 15));
		asignacion = reloj("R-ATE-1", paciente, TOKEN_RELOJ);
		reloj("R-ATE-2", paciente("11111111-1", "hash-atencion-2", "Otra", "Persona", null), TOKEN_OTRO_RELOJ);
		coordinadora = usuario(RUN_COORDINADORA, "COORDINADOR", "Camila");
		personal = usuario(RUN_PERSONAL, "PERSONAL", "Diego");
		tokenCoordinadora = token(RUN_COORDINADORA, "Camila");
		tokenPersonal = token(RUN_PERSONAL, "Diego");
	}

	// ---------- R1: línea de tiempo ----------

	/** R1: el recorrido completo de una alerta aparece en orden, con sus saltos de contacto. */
	@Test
	void laLineaDeTiempoMuestraTodoElRecorridoEnOrden() throws Exception {
		Instant t0 = ahora.minus(1, ChronoUnit.HOURS);
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", t0);
		jdbc.sql("UPDATE alerta SET fecha_hora_agravamiento = :f WHERE id_alerta = :a")
				.param("f", marca(t0, 1)).param("a", alerta).update();
		UUID hija = contactoDelPaciente("Hija Ana", "hija@correo.cl", 1);
		UUID vecino = contactoDelPaciente("Vecino Pedro", "vecino@correo.cl", 2);
		UUID primero = intento(alerta, hija, "WHATSAPP", 1, t0, 2);
		jdbc.sql("""
				UPDATE registro_intento SET fecha_hora_entrega = :e, fecha_hora_lectura = :l,
					respuesta = 'NO_PUEDO', fecha_hora_respuesta = :r WHERE id_intento = :i""")
				.param("e", marca(t0, 3)).param("l", marca(t0, 4)).param("r", marca(t0, 5)).param("i", primero)
				.update();
		UUID segundo = intento(alerta, vecino, "CORREO", 1, t0, 6);
		jdbc.sql("""
				UPDATE registro_intento SET fecha_hora_fallo = :f, motivo_fallo = 'Buzón lleno'
				WHERE id_intento = :i""").param("f", marca(t0, 7)).param("i", segundo).update();
		UUID tercero = intento(alerta, hija, "WHATSAPP", 2, t0, 8);
		jdbc.sql("""
				UPDATE registro_intento SET respuesta = 'VOY_EN_CAMINO', fecha_hora_respuesta = :r
				WHERE id_intento = :i""").param("r", marca(t0, 9)).param("i", tercero).update();
		jdbc.sql("""
				INSERT INTO aviso_fcm (id_alerta, tipo, fecha_hora_envio, fecha_hora_recepcion, intentos)
				VALUES (:a, 'ALERTA_CONFIRMADA', :e, :r, 1)""")
				.param("a", alerta).param("e", marca(t0, 10)).param("r", marca(t0, 11)).update();
		jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, id_usuario_responsable, fecha_hora_toma)
				VALUES (:a, :u, :t)""").param("a", alerta).param("u", personal).param("t", marca(t0, 12)).update();
		jdbc.sql("""
				INSERT INTO seguimiento (id_alerta, id_usuario, tipo, nota, fecha_hora)
				VALUES (:a, :u, 'LLAMADA', :n, :f)""")
				.param("a", alerta).param("u", personal)
				.param("n", cifrado.cifrar("Llamada a la hija", ColumnaCifrada.SEGUIMIENTO_NOTA))
				.param("f", marca(t0, 13)).update();
		resolver(tokenCoordinadora, alerta, "\"POSITIVA\"", OBSERVACION).andExpect(status().isNoContent());

		detalle(tokenCoordinadora, alerta).andExpect(status().isOk())
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", contains("ALERTA_RECIBIDA", "ALERTA_AGRAVADA",
						"AVISO_ENVIADO", "AVISO_ENTREGADO", "AVISO_LEIDO", "RESPUESTA_CONTACTO", "SALTO_CONTACTO",
						"AVISO_ENVIADO", "AVISO_FALLIDO", "SALTO_CONTACTO", "AVISO_ENVIADO", "RESPUESTA_CONTACTO",
						"FCM_ENVIADO", "FCM_RECIBIDO", "CASO_ASUMIDO", "SEGUIMIENTO", "ALERTA_RESUELTA",
						"FCM_PENDIENTE")))
				.andExpect(jsonPath("$.lineaTiempo[2].contacto").value("Hija Ana"))
				.andExpect(jsonPath("$.lineaTiempo[2].canal").value("WHATSAPP"))
				.andExpect(jsonPath("$.lineaTiempo[2].ronda").value(1))
				.andExpect(jsonPath("$.lineaTiempo[5].detalle").value("NO_PUEDO"))
				.andExpect(jsonPath("$.lineaTiempo[6].contacto").value("Vecino Pedro"))
				.andExpect(jsonPath("$.lineaTiempo[8].detalle").value("Buzón lleno"))
				.andExpect(jsonPath("$.lineaTiempo[10].ronda").value(2))
				.andExpect(jsonPath("$.lineaTiempo[12].detalle").value("ALERTA_CONFIRMADA"))
				.andExpect(jsonPath("$.lineaTiempo[14].usuario").value("Diego"))
				.andExpect(jsonPath("$.lineaTiempo[15].detalle").value("LLAMADA: Llamada a la hija"))
				.andExpect(jsonPath("$.lineaTiempo[16].usuario").value("Camila"))
				.andExpect(jsonPath("$.lineaTiempo[17].detalle").value("ALERTA_RESUELTA"));
	}

	/** R1: el respaldo por correo al mismo contacto y los avisos al equipo no son un salto de contacto. */
	@Test
	void elRespaldoYLosAvisosAlEquipoNoSonSaltos() throws Exception {
		Instant t0 = ahora.minus(1, ChronoUnit.HOURS);
		UUID alerta = alertaDelReloj("FC", "GRAVE", t0);
		UUID hija = contactoDelPaciente("Hija Ana", "hija@correo.cl", 1);
		intento(alerta, hija, "WHATSAPP", 1, t0, 1);
		UUID interno = jdbc.sql("INSERT INTO contacto (id_usuario) VALUES (:u) RETURNING id_contacto")
				.param("u", coordinadora).query(UUID.class).single();
		jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_interno, canal, fecha_hora_envio)
				VALUES (gen_random_uuid(), :a, :c, 'CORREO', :e)""")
				.param("a", alerta).param("c", interno).param("e", marca(t0, 2)).update();
		intento(alerta, hija, "CORREO", 1, t0, 3);

		detalle(tokenCoordinadora, alerta)
				.andExpect(jsonPath("$.lineaTiempo[*].tipo",
						contains("ALERTA_RECIBIDA", "AVISO_ENVIADO", "AVISO_ENVIADO", "AVISO_ENVIADO")))
				.andExpect(jsonPath("$.lineaTiempo[2].contacto").value("Camila"))
				.andExpect(jsonPath("$.lineaTiempo[2].ronda").value(nullValue()));
	}

	/**
	 * R1: una alerta cancelada muestra la cancelación, que ocurrió en el reloj antes de que llegara al
	 * servidor; una con la lista terminada, el sin respuesta.
	 */
	@Test
	void laLineaDeTiempoMuestraCancelacionYSinRespuesta() throws Exception {
		UUID eventId = UUID.randomUUID();
		cancelacion(eventId).andExpect(status().isCreated());
		detalle(tokenCoordinadora, idDe(eventId))
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", contains("ALERTA_CANCELADA", "ALERTA_RECIBIDA")));

		UUID otra = UUID.randomUUID();
		alertaPorApi(otra, "CAIDA", "GRAVE");
		sinRespuesta(TOKEN_RELOJ, otra, ahora.plusSeconds(30)).andExpect(status().isOk());
		detalle(tokenCoordinadora, idDe(otra))
				.andExpect(jsonPath("$.estadoContacto").value("SIN_RESPUESTA"))
				.andExpect(jsonPath("$.prioridadMaxima").value(true))
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", hasSize(2)))
				.andExpect(jsonPath("$.lineaTiempo[1].tipo").value("SIN_RESPUESTA"));
	}

	/** R1: una alerta que no existe responde 404. */
	@Test
	void elDetalleDeUnaAlertaQueNoExisteEs404() throws Exception {
		detalle(tokenCoordinadora, UUID.randomUUID()).andExpect(status().isNotFound());
	}

	// ---------- R2: qué exige resolver ----------

	/** R2: una alerta de salud sin clasificación se rechaza y no guarda nada. */
	@Test
	void unaAlertaDeSaludExigeClasificacion() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		resolver(tokenCoordinadora, alerta, "null", OBSERVACION).andExpect(status().isBadRequest());

		assertThat(filasAtencion(alerta)).isZero();
		assertThat(avisosFcm(alerta)).isZero();
	}

	/** R2: las observaciones son obligatorias, también en una alerta técnica; solo espacios no sirve. */
	@Test
	void lasObservacionesSonObligatoriasEnTodas() throws Exception {
		UUID salud = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		UUID tecnica = alertaTecnica("BATERIA_BAJA");

		resolver(tokenCoordinadora, salud, "\"FALSA_ALARMA\"", "   ").andExpect(status().isBadRequest());
		resolver(tokenCoordinadora, tecnica, "null", "").andExpect(status().isBadRequest());
		mvc.perform(post("/api/alertas/" + tecnica + "/resolver").header("Authorization", "Bearer " + tokenCoordinadora)
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

		assertThat(filasAtencion(salud)).isZero();
		assertThat(filasAtencion(tecnica)).isZero();
	}

	/** R2: una alerta técnica no se clasifica. */
	@Test
	void unaAlertaTecnicaNoSeClasifica() throws Exception {
		UUID tecnica = alertaTecnica("SILENCIO_DATOS");

		resolver(tokenCoordinadora, tecnica, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isBadRequest());
		assertThat(filasAtencion(tecnica)).isZero();

		resolver(tokenCoordinadora, tecnica, "null", OBSERVACION).andExpect(status().isNoContent());
		detalle(tokenCoordinadora, tecnica).andExpect(jsonPath("$.estadoAtencion").value("RESUELTA"))
				.andExpect(jsonPath("$.atencion.clasificacion").value(nullValue()));
	}

	/** R2: una clasificación que no existe se rechaza. */
	@Test
	void unaClasificacionInventadaSeRechaza() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		resolver(tokenCoordinadora, alerta, "\"QUIZAS\"", OBSERVACION).andExpect(status().isBadRequest());
		assertThat(filasAtencion(alerta)).isZero();
	}

	/** R2: en la base las observaciones van cifradas; por la API se leen en claro. */
	@Test
	void lasObservacionesSeGuardanCifradas() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		resolver(tokenCoordinadora, alerta, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isNoContent());

		String guardado = jdbc.sql("SELECT observaciones_cierre FROM atencion_alerta WHERE id_alerta = :a")
				.param("a", alerta).query(String.class).single();
		assertThat(guardado).startsWith("v1:").doesNotContain("hija");
		detalle(tokenCoordinadora, alerta).andExpect(jsonPath("$.atencion.observaciones").value(OBSERVACION));
	}

	// ---------- R3: quién resuelve y quién queda responsable ----------

	/** R3: sin nadie a cargo, quien resuelve queda como responsable y como resolutor. */
	@Test
	void quienResuelveSinResponsableQuedaAcargo() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		resolver(tokenPersonal, alerta, "\"POSITIVA\"", OBSERVACION).andExpect(status().isNoContent());

		detalle(tokenCoordinadora, alerta).andExpect(jsonPath("$.responsable").value("Diego"))
				.andExpect(jsonPath("$.atencion.resolutor").value("Diego"))
				.andExpect(jsonPath("$.atencion.clasificacion").value("POSITIVA"));
	}

	/** R3: otra persona con permiso puede cerrar un caso ajeno; el responsable no cambia. */
	@Test
	void otraPersonaResuelveYElResponsableSeConserva() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		asumir(tokenPersonal, alerta).andExpect(status().isNoContent());

		resolver(tokenCoordinadora, alerta, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isNoContent());

		detalle(tokenCoordinadora, alerta).andExpect(jsonPath("$.responsable").value("Diego"))
				.andExpect(jsonPath("$.atencion.resolutor").value("Camila"));
	}

	// ---------- R4: lo que no se puede resolver ----------

	/** R4: una alerta que no existe responde 404. */
	@Test
	void resolverUnaAlertaQueNoExisteEs404() throws Exception {
		resolver(tokenCoordinadora, UUID.randomUUID(), "\"POSITIVA\"", OBSERVACION).andExpect(status().isNotFound());
	}

	/** R4: una alerta cancelada por el paciente no tiene atención. */
	@Test
	void unaAlertaCanceladaNoSeResuelve() throws Exception {
		UUID eventId = UUID.randomUUID();
		cancelacion(eventId).andExpect(status().isCreated());

		resolver(tokenCoordinadora, idDe(eventId), "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isConflict());
		assertThat(filasAtencion(idDe(eventId))).isZero();
	}

	/** R4: no se resuelve dos veces: lo primero que se escribió se conserva. */
	@Test
	void noSeResuelveDosVeces() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		resolver(tokenCoordinadora, alerta, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isNoContent());

		resolver(tokenPersonal, alerta, "\"POSITIVA\"", "Otra cosa").andExpect(status().isConflict());

		detalle(tokenCoordinadora, alerta).andExpect(jsonPath("$.atencion.resolutor").value("Camila"))
				.andExpect(jsonPath("$.atencion.clasificacion").value("FALSA_ALARMA"));
	}

	/** R4: una técnica que el sistema ya cerró (volvieron los datos) no se resuelve. */
	@Test
	void unaTecnicaCerradaPorElSistemaNoSeResuelve() throws Exception {
		UUID tecnica = alertaTecnica("BATERIA_BAJA");
		jdbc.sql("INSERT INTO atencion_alerta (id_alerta, fecha_hora_cierre) VALUES (:a, :c)")
				.param("a", tecnica).param("c", Timestamp.from(ahora)).update();

		resolver(tokenCoordinadora, tecnica, "null", OBSERVACION).andExpect(status().isConflict());
	}

	/** R4 y R5: dos personas a la vez: una la resuelve, la otra recibe 409, y hay un solo aviso. */
	@Test
	void dosPersonasALaVezSoloUnaResuelve() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		List<Integer> estados = aLaVez(
				() -> resolver(tokenCoordinadora, alerta, "\"FALSA_ALARMA\"", OBSERVACION),
				() -> resolver(tokenPersonal, alerta, "\"POSITIVA\"", OBSERVACION));

		assertThat(estados).containsExactlyInAnyOrder(204, 409);
		assertThat(avisosFcm(alerta)).isEqualTo(1);
	}

	// ---------- R5: aviso FCM pendiente ----------

	/** R5: resolver una alerta del reloj deja pendiente el aviso, sin fecha de envío. */
	@Test
	void resolverDejaElAvisoAlRelojPendiente() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		resolver(tokenCoordinadora, alerta, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isNoContent());

		assertThat(jdbc.sql("""
				SELECT count(*) FROM aviso_fcm WHERE id_alerta = :a AND tipo = 'ALERTA_RESUELTA'
					AND fecha_hora_envio IS NULL AND intentos = 0""")
				.param("a", alerta).query(Integer.class).single()).isEqualTo(1);
	}

	/** R5: una alerta técnica del servidor no avisa al reloj: el reloj no la escala. */
	@Test
	void unaTecnicaDelServidorNoDejaAvisoAlReloj() throws Exception {
		UUID tecnica = alertaTecnica("SILENCIO_DATOS");

		resolver(tokenCoordinadora, tecnica, "null", OBSERVACION).andExpect(status().isNoContent());

		assertThat(avisosFcm(tecnica)).isZero();
		detalle(tokenCoordinadora, tecnica)
				.andExpect(jsonPath("$.lineaTiempo[*].tipo", contains("ALERTA_RECIBIDA", "ALERTA_RESUELTA")));
	}

	// ---------- R6: seguimiento ----------

	/** R6: una POSITIVA abre su seguimiento y queda resuelta al cerrarlo; no se puede cerrar dos veces. */
	@Test
	void unaPositivaQuedaEnSeguimientoHastaCerrarlo() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		resolver(tokenCoordinadora, alerta, "\"POSITIVA\"", OBSERVACION).andExpect(status().isNoContent());
		detalle(tokenCoordinadora, alerta).andExpect(jsonPath("$.estadoAtencion").value("EN_SEGUIMIENTO"))
				.andExpect(jsonPath("$.estadoContacto").value("RESUELTA"));

		cerrarSeguimiento(tokenPersonal, alerta).andExpect(status().isNoContent());

		detalle(tokenCoordinadora, alerta).andExpect(jsonPath("$.estadoAtencion").value("RESUELTA"))
				.andExpect(jsonPath("$.lineaTiempo[-1:].tipo", contains("SEGUIMIENTO_CERRADO")))
				.andExpect(jsonPath("$.lineaTiempo[-1:].usuario", contains(nullValue())));
		cerrarSeguimiento(tokenPersonal, alerta).andExpect(status().isConflict());
	}

	/** R6: una FALSA_ALARMA no tiene seguimiento que cerrar, ni tampoco una alerta sin resolver. */
	@Test
	void soloSeCierraElSeguimientoDeUnaPositiva() throws Exception {
		UUID falsa = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		resolver(tokenCoordinadora, falsa, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isNoContent());
		detalle(tokenCoordinadora, falsa).andExpect(jsonPath("$.estadoAtencion").value("RESUELTA"));

		cerrarSeguimiento(tokenCoordinadora, falsa).andExpect(status().isConflict());
		cerrarSeguimiento(tokenCoordinadora, alertaDelReloj("FC", "MEDIA", ahora.minusSeconds(30)))
				.andExpect(status().isConflict());
		cerrarSeguimiento(tokenCoordinadora, UUID.randomUUID()).andExpect(status().isNotFound());
	}

	// ---------- R7: asumir ----------

	/** R7: asumir deja la alerta en atención, con su responsable. */
	@Test
	void asumirDejaLaAlertaEnAtencion() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		detalle(tokenCoordinadora, alerta).andExpect(jsonPath("$.estadoAtencion").value("SIN_ASIGNAR"));

		asumir(tokenPersonal, alerta).andExpect(status().isNoContent());

		detalle(tokenCoordinadora, alerta).andExpect(jsonPath("$.estadoAtencion").value("EN_ATENCION"))
				.andExpect(jsonPath("$.responsable").value("Diego"));
	}

	/** R7: lo ya asumido, resuelto o cancelado responde 409; lo que no existe, 404. */
	@Test
	void noSeAsumeLoQueYaTieneDuenoOTermino() throws Exception {
		UUID tomada = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		asumir(tokenPersonal, tomada).andExpect(status().isNoContent());
		asumir(tokenCoordinadora, tomada).andExpect(status().isConflict());
		detalle(tokenCoordinadora, tomada).andExpect(jsonPath("$.responsable").value("Diego"));

		UUID cerrada = alertaDelReloj("FC", "MEDIA", ahora.minusSeconds(50));
		resolver(tokenCoordinadora, cerrada, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isNoContent());
		asumir(tokenPersonal, cerrada).andExpect(status().isConflict());

		UUID eventId = UUID.randomUUID();
		cancelacion(eventId).andExpect(status().isCreated());
		asumir(tokenPersonal, idDe(eventId)).andExpect(status().isConflict());

		asumir(tokenPersonal, UUID.randomUUID()).andExpect(status().isNotFound());
	}

	/** R7: una alerta técnica también se asume. */
	@Test
	void unaTecnicaTambienSeAsume() throws Exception {
		UUID tecnica = alertaTecnica("BATERIA_BAJA");

		asumir(tokenPersonal, tecnica).andExpect(status().isNoContent());

		detalle(tokenCoordinadora, tecnica).andExpect(jsonPath("$.estadoAtencion").value("EN_ATENCION"));
	}

	/** R7: de dos personas a la vez, solo una toma el caso. */
	@Test
	void dosPersonasALaVezSoloUnaAsume() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		List<Integer> estados = aLaVez(() -> asumir(tokenCoordinadora, alerta), () -> asumir(tokenPersonal, alerta));

		assertThat(estados).containsExactlyInAnyOrder(204, 409);
	}

	// ---------- R8: el reloj informa el fin de la lista ----------

	/** R8: la alerta pasa a SIN_RESPUESTA; repetirlo responde 200 y conserva la primera hora. */
	@Test
	void sinRespuestaEsRepetibleYConservaLaHora() throws Exception {
		UUID eventId = UUID.randomUUID();
		alertaPorApi(eventId, "CAIDA", "GRAVE");
		Instant primera = ahora.minusSeconds(5);

		sinRespuesta(TOKEN_RELOJ, eventId, primera).andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(eventId.toString()))
				.andExpect(jsonPath("$.estado").value("SIN_RESPUESTA"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(false));
		sinRespuesta(TOKEN_RELOJ, eventId, ahora).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("SIN_RESPUESTA"));

		assertThat(jdbc.sql("SELECT fecha_hora_sin_respuesta FROM alerta WHERE event_id = :e")
				.param("e", eventId).query(Timestamp.class).single().toInstant()).isEqualTo(primera);
	}

	/** R8: otro reloj o un eventId desconocido responden 404 con su código, sin cambiar nada. */
	@Test
	void sinRespuestaDeOtroRelojEs404() throws Exception {
		UUID eventId = UUID.randomUUID();
		alertaPorApi(eventId, "CAIDA", "GRAVE");

		sinRespuesta(TOKEN_OTRO_RELOJ, eventId, ahora).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("ALERTA_DESCONOCIDA"));
		sinRespuesta(TOKEN_RELOJ, UUID.randomUUID(), ahora).andExpect(status().isNotFound());

		detalle(tokenCoordinadora, idDe(eventId)).andExpect(jsonPath("$.estadoContacto").value("RECIBIDA"));
	}

	/** R8: una alerta cancelada no admite el fin de la lista. */
	@Test
	void sinRespuestaDeUnaCanceladaEs409() throws Exception {
		UUID eventId = UUID.randomUUID();
		cancelacion(eventId).andExpect(status().isCreated());

		sinRespuesta(TOKEN_RELOJ, eventId, ahora).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NO_APLICA"));
	}

	/** R8: sin fechaHora el mensaje está mal formado. */
	@Test
	void sinRespuestaExigeLaHora() throws Exception {
		UUID eventId = UUID.randomUUID();
		alertaPorApi(eventId, "CAIDA", "GRAVE");

		mvc.perform(post("/api/reloj/v1/alertas/" + eventId + "/sin-respuesta")
				.header("Authorization", "Bearer " + TOKEN_RELOJ).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("MENSAJE_INVALIDO"));
	}

	// ---------- R9: lista ----------

	/** R9: cada fila trae el formato que espera el front, con la edad calculada y sin datos de más. */
	@Test
	void laListaUsaElFormatoDelFront() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		asumir(tokenPersonal, alerta).andExpect(status().isNoContent());
		int edad = Period.between(LocalDate.of(1940, 3, 15), LocalDate.now(ZonaHoraria.CHILE)).getYears();

		lista(tokenCoordinadora).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(alerta.toString()))
				.andExpect(jsonPath("$[0].numero").isNumber())
				.andExpect(jsonPath("$[0].paciente.id").value(paciente.toString()))
				.andExpect(jsonPath("$[0].paciente.nombres").value("María"))
				.andExpect(jsonPath("$[0].paciente.apellidos").value("Soto Rojas"))
				.andExpect(jsonPath("$[0].paciente.edad").value(edad))
				.andExpect(jsonPath("$[0].tipoEvento").value("CAIDA"))
				.andExpect(jsonPath("$[0].severidad").value("MEDIA"))
				.andExpect(jsonPath("$[0].estadoAtencion").value("EN_ATENCION"))
				.andExpect(jsonPath("$[0].responsable").value("Diego"))
				.andExpect(jsonPath("$[0].recepcion").value(ahora.minusSeconds(60).toString()))
				.andExpect(jsonPath("$[0].prioridadMaxima").value(false));
	}

	/** R9: la gravedad que se muestra es la actual: una caída agravada figura GRAVE. */
	@Test
	void laListaMuestraLaGravedadActual() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));
		jdbc.sql("UPDATE alerta SET fecha_hora_agravamiento = :f WHERE id_alerta = :a")
				.param("f", Timestamp.from(ahora)).param("a", alerta).update();

		lista(tokenCoordinadora).andExpect(jsonPath("$[0].severidad").value("GRAVE"));
	}

	/** R9: primero las SIN_RESPUESTA abiertas, aunque otra sea más grave o más nueva; luego las recientes. */
	@Test
	void lasSinRespuestaAbiertasVanPrimero() throws Exception {
		UUID vieja = alertaDelReloj("CAIDA", "LEVE", ahora.minus(3, ChronoUnit.HOURS));
		UUID media = alertaDelReloj("FC", "MEDIA", ahora.minus(2, ChronoUnit.HOURS));
		UUID nueva = alertaDelReloj("CAIDA", "GRAVE", ahora.minus(1, ChronoUnit.HOURS));
		marcarSinRespuesta(vieja);

		lista(tokenCoordinadora)
				.andExpect(jsonPath("$[*].id", contains(vieja.toString(), nueva.toString(), media.toString())))
				.andExpect(jsonPath("$[0].prioridadMaxima").value(true))
				.andExpect(jsonPath("$[1].prioridadMaxima").value(false));
	}

	/** R9: al resolverla, una SIN_RESPUESTA pierde la prioridad y vuelve a su lugar por fecha. */
	@Test
	void alResolverlaPierdeLaPrioridad() throws Exception {
		UUID vieja = alertaDelReloj("CAIDA", "LEVE", ahora.minus(3, ChronoUnit.HOURS));
		UUID nueva = alertaDelReloj("FC", "MEDIA", ahora.minus(1, ChronoUnit.HOURS));
		marcarSinRespuesta(vieja);

		resolver(tokenCoordinadora, vieja, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isNoContent());

		lista(tokenCoordinadora).andExpect(jsonPath("$[*].id", contains(nueva.toString(), vieja.toString())))
				.andExpect(jsonPath("$[1].prioridadMaxima").value(false));
	}

	/** R9: las canceladas solo sirven para métricas: no aparecen como trabajo pendiente. */
	@Test
	void lasCanceladasNoAparecenEnLaLista() throws Exception {
		UUID eventId = UUID.randomUUID();
		cancelacion(eventId).andExpect(status().isCreated());
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		lista(tokenCoordinadora).andExpect(jsonPath("$[*].id", contains(alerta.toString())))
				.andExpect(jsonPath("$[*].id", not(contains(idDe(eventId).toString()))));
	}

	/** R9: una alerta técnica también aparece, y sin paciente con edad conocida la edad es null. */
	@Test
	void laListaIncluyeLasTecnicas() throws Exception {
		jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, origen, tipo_evento, severidad, fecha_hora_recepcion)
				SELECT gen_random_uuid(), a.id_asignacion, 'SERVIDOR', 'SILENCIO_DATOS', 'MEDIA', :r
				FROM asignacion_reloj a WHERE a.id_asignacion <> :propia""")
				.param("r", Timestamp.from(ahora)).param("propia", asignacion).update();

		lista(tokenCoordinadora).andExpect(jsonPath("$[0].tipoEvento").value("SILENCIO_DATOS"))
				.andExpect(jsonPath("$[0].paciente.edad").value(nullValue()))
				.andExpect(jsonPath("$[0].responsable").value(nullValue()));
	}

	// ---------- R10: permisos y auditoría ----------

	/** R10: sin token, 401 en todo. */
	@Test
	void sinTokenResponde401() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		mvc.perform(get("/api/alertas")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/alertas/" + alerta)).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/alertas/" + alerta + "/asumir")).andExpect(status().isUnauthorized());
	}

	/** R10: con solo ALERTAS:VER se ve pero no se actúa; sin él, ni se ve. */
	@Test
	void verYActuarExigenSusPermisos() throws Exception {
		rolConPermiso("SOLO_VER", "ALERTAS", "VER");
		rolConPermiso("SIN_ALERTAS", "USUARIOS", "VER");
		usuario(RUN_SOLO_VER, "SOLO_VER", "Lectora");
		usuario(RUN_SIN_ALERTAS, "SIN_ALERTAS", "Ajena");
		String soloVer = token(RUN_SOLO_VER, "Lectora");
		String sinAlertas = token(RUN_SIN_ALERTAS, "Ajena");
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		lista(soloVer).andExpect(status().isOk());
		detalle(soloVer, alerta).andExpect(status().isOk());
		asumir(soloVer, alerta).andExpect(status().isForbidden());
		resolver(soloVer, alerta, "\"FALSA_ALARMA\"", OBSERVACION).andExpect(status().isForbidden());
		cerrarSeguimiento(soloVer, alerta).andExpect(status().isForbidden());

		lista(sinAlertas).andExpect(status().isForbidden());
		detalle(sinAlertas, alerta).andExpect(status().isForbidden());
		assertThat(filasAtencion(alerta)).isZero();
	}

	/** R10: leer el detalle queda registrado con el id de la alerta, y la lista también. */
	@Test
	void lasLecturasQuedanAuditadas() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		lista(tokenCoordinadora).andExpect(status().isOk());
		detalle(tokenCoordinadora, alerta).andExpect(status().isOk());

		assertThat(auditoria("LEER", "ALERTA", null)).isEqualTo(1);
		assertThat(auditoria("LEER", "ALERTA", alerta)).isEqualTo(1);
	}

	/** R10: asumir, resolver y cerrar el seguimiento quedan registrados, sin las observaciones. */
	@Test
	void losCambiosQuedanAuditadosSinDatosPersonales() throws Exception {
		UUID alerta = alertaDelReloj("CAIDA", "MEDIA", ahora.minusSeconds(60));

		asumir(tokenPersonal, alerta).andExpect(status().isNoContent());
		resolver(tokenCoordinadora, alerta, "\"POSITIVA\"", OBSERVACION).andExpect(status().isNoContent());
		cerrarSeguimiento(tokenCoordinadora, alerta).andExpect(status().isNoContent());

		assertThat(auditoria("MODIFICAR", "ATENCION_ALERTA", alerta)).isEqualTo(3);
		assertThat(jdbc.sql("SELECT count(*) FROM registro_auditoria WHERE detalle::text LIKE '%hija%'")
				.query(Integer.class).single()).isZero();
	}

	// ---------- Apoyo ----------

	/** Pide la lista de alertas. */
	private ResultActions lista(String token) throws Exception {
		return mvc.perform(get("/api/alertas").header("Authorization", "Bearer " + token));
	}

	/** Pide el detalle de una alerta. */
	private ResultActions detalle(String token, UUID alerta) throws Exception {
		return mvc.perform(get("/api/alertas/" + alerta).header("Authorization", "Bearer " + token));
	}

	/** Asume el caso. */
	private ResultActions asumir(String token, UUID alerta) throws Exception {
		return mvc.perform(post("/api/alertas/" + alerta + "/asumir").header("Authorization", "Bearer " + token));
	}

	/**
	 * Resuelve la alerta.
	 *
	 * @param clasificacion valor JSON tal cual: con comillas, o null
	 */
	private ResultActions resolver(String token, UUID alerta, String clasificacion, String observaciones)
			throws Exception {
		return mvc.perform(post("/api/alertas/" + alerta + "/resolver").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"clasificacion\": %s, \"observaciones\": \"%s\"}".formatted(clasificacion, observaciones)));
	}

	/** Cierra el seguimiento de una alerta POSITIVA. */
	private ResultActions cerrarSeguimiento(String token, UUID alerta) throws Exception {
		return mvc.perform(post("/api/alertas/" + alerta + "/seguimiento/cerrar")
				.header("Authorization", "Bearer " + token));
	}

	/** El reloj informa que recorrió la lista dos veces sin confirmación. */
	private ResultActions sinRespuesta(String token, UUID eventId, Instant fecha) throws Exception {
		return mvc.perform(post("/api/reloj/v1/alertas/" + eventId + "/sin-respuesta")
				.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaHora\": \"" + fecha + "\"}"));
	}

	/** Envía una alerta con la API del reloj. */
	private void alertaPorApi(UUID eventId, String tipo, String severidad) throws Exception {
		mvc.perform(post("/api/reloj/v1/alertas").header("Authorization", "Bearer " + TOKEN_RELOJ)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"eventId": "%s", "tipoEvento": "%s", "severidad": "%s", "versionConfiguracion": "v",
						"fechaHoraDeteccion": "%s", "fechaHoraEnvio": "%s", "esperaDiaHabil": false}"""
						.formatted(eventId, tipo, severidad, ahora.minusSeconds(40), ahora.minusSeconds(10))))
				.andExpect(status().isCreated());
	}

	/** Informa una caída que la paciente canceló en los 30 segundos. */
	private ResultActions cancelacion(UUID eventId) throws Exception {
		return mvc.perform(post("/api/reloj/v1/cancelaciones").header("Authorization", "Bearer " + TOKEN_RELOJ)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"eventId": "%s", "tipoEvento": "CAIDA", "severidad": "MEDIA", "versionConfiguracion": "v",
						"fechaHoraDeteccion": "%s", "fechaHoraCancelacion": "%s"}"""
						.formatted(eventId, ahora.minusSeconds(40), ahora.minusSeconds(20))));
	}

	/**
	 * Ejecuta las dos acciones al mismo tiempo.
	 *
	 * @return el código HTTP de cada una
	 */
	private static List<Integer> aLaVez(Callable<ResultActions> primera, Callable<ResultActions> segunda)
			throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Future<ResultActions>> enCurso = hilos.invokeAll(List.of(primera, segunda));
			List<Integer> estados = new ArrayList<>();
			for (Future<ResultActions> accion : enCurso) {
				estados.add(accion.get().andReturn().getResponse().getStatus());
			}
			return estados;
		} finally {
			hilos.shutdown();
		}
	}

	/** @return una alerta del reloj de la paciente, recibida a esa hora */
	private UUID alertaDelReloj(String tipo, String severidad, Instant recepcion) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
					fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', :t, :s, :r) RETURNING id_alerta""")
				.param("a", asignacion).param("t", tipo).param("s", severidad)
				.param("r", Timestamp.from(recepcion)).query(UUID.class).single();
	}

	/** @return una alerta técnica creada por el servidor hace un minuto */
	private UUID alertaTecnica(String tipo) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, origen, tipo_evento, severidad, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'SERVIDOR', :t, 'LEVE', :r) RETURNING id_alerta""")
				.param("a", asignacion).param("t", tipo)
				.param("r", Timestamp.from(ahora.minusSeconds(60))).query(UUID.class).single();
	}

	/** Deja la alerta como si el reloj hubiera terminado la lista. */
	private void marcarSinRespuesta(UUID alerta) {
		jdbc.sql("UPDATE alerta SET fecha_hora_sin_respuesta = :f WHERE id_alerta = :a")
				.param("f", Timestamp.from(ahora)).param("a", alerta).update();
	}

	/** @return el vínculo de una persona nueva con la lista de la paciente */
	private UUID contactoDelPaciente(String nombre, String correo, int prioridad) {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, correo, correo_hash) VALUES (:n, :c, :h) RETURNING id_contacto""")
				.param("n", cifrado.cifrar(nombre, ColumnaCifrada.CONTACTO_NOMBRE))
				.param("c", cifrado.cifrar(correo, ColumnaCifrada.CONTACTO_CORREO))
				.param("h", "hash-" + correo).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad)
				VALUES (:p, :c, 'VECINO', :o) RETURNING id_contacto_paciente""")
				.param("p", paciente).param("c", persona).param("o", prioridad).query(UUID.class).single();
	}

	/** @return un aviso enviado a ese contacto, tantos minutos después de t0 */
	private UUID intento(UUID alerta, UUID vinculo, String canal, int ronda, Instant t0, int minutos) {
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda, fecha_hora_envio)
				VALUES (gen_random_uuid(), :a, :c, :canal, :r, :e) RETURNING id_intento""")
				.param("a", alerta).param("c", vinculo).param("canal", canal).param("r", ronda)
				.param("e", marca(t0, minutos)).query(UUID.class).single();
	}

	/** @return el instante tantos minutos después de t0, para JDBC */
	private static Timestamp marca(Instant t0, int minutos) {
		return Timestamp.from(t0.plus(minutos, ChronoUnit.MINUTES));
	}

	/** @return el identificador interno de la alerta con ese eventId */
	private UUID idDe(UUID eventId) {
		return jdbc.sql("SELECT id_alerta FROM alerta WHERE event_id = :e").param("e", eventId)
				.query(UUID.class).single();
	}

	/** @return cuántas filas de atención tiene la alerta */
	private int filasAtencion(UUID alerta) {
		return jdbc.sql("SELECT count(*) FROM atencion_alerta WHERE id_alerta = :a").param("a", alerta)
				.query(Integer.class).single();
	}

	/** @return cuántos avisos FCM tiene la alerta */
	private int avisosFcm(UUID alerta) {
		return jdbc.sql("SELECT count(*) FROM aviso_fcm WHERE id_alerta = :a").param("a", alerta)
				.query(Integer.class).single();
	}

	/**
	 * @param idEntidad el objeto auditado; null para las lecturas de una lista
	 * @return cuántos registros de auditoría hay con esos datos
	 */
	private int auditoria(String accion, String entidad, UUID idEntidad) {
		return jdbc.sql("""
				SELECT count(*) FROM registro_auditoria
				WHERE accion = :a AND entidad = :e AND id_entidad = :i""")
				.param("a", accion).param("e", entidad)
				.param("i", idEntidad == null ? "LISTA" : idEntidad.toString())
				.query(Integer.class).single();
	}

	/** Crea un rol de prueba con un único permiso. */
	private void rolConPermiso(String codigo, String modulo, String accion) {
		jdbc.sql("INSERT INTO rol (codigo, nombre) VALUES (:c, :c)").param("c", codigo).update();
		jdbc.sql("INSERT INTO permiso_rol (codigo_rol, modulo, accion) VALUES (:c, :m, :a)")
				.param("c", codigo).param("m", modulo).param("a", accion).update();
	}

	/** @return una cuenta activa con esa clave derivada de su nombre */
	private UUID usuario(String run, String rol, String nombre) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :clave, true) RETURNING id_usuario""")
				.param("rol", rol)
				.param("hash", hashRun.de(Run.de(run)))
				.param("nombre", nombre)
				.param("correo", nombre.toLowerCase() + "@muni.cl")
				.param("clave", encoder.encode("Clave-de-" + nombre + "1"))
				.query(UUID.class).single();
	}

	/** Inicia sesión y devuelve el token de acceso. */
	private String token(String run, String nombre) throws Exception {
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"Clave-de-" + nombre + "1\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.token");
	}

	/** @return una paciente con consentimiento y sus datos cifrados de verdad */
	private UUID paciente(String run, String hash, String nombres, String apellidos, LocalDate nacimiento) {
		return jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, nombres, apellidos, fecha_nacimiento, fecha_consentimiento,
					fecha_ingreso)
				VALUES (:rut, :hash, :nombres, :apellidos, :nacimiento, CURRENT_DATE, CURRENT_DATE)
				RETURNING id_paciente""")
				.param("rut", cifrado.cifrar(run, ColumnaCifrada.PACIENTE_RUT))
				.param("hash", hash)
				.param("nombres", cifrado.cifrar(nombres, ColumnaCifrada.PACIENTE_NOMBRES))
				.param("apellidos", cifrado.cifrar(apellidos, ColumnaCifrada.PACIENTE_APELLIDOS))
				.param("nacimiento", cifrado.cifrar(nacimiento == null ? null : nacimiento.toString(),
						ColumnaCifrada.PACIENTE_FECHA_NACIMIENTO))
				.query(UUID.class).single();
	}

	/** @return la asignación de un reloj nuevo a esa paciente, desde hace un día, con ese token */
	private UUID reloj(String identificador, UUID idPaciente, String token) {
		UUID dispositivo = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:id, 'OPERATIVO')
				RETURNING id_dispositivo""").param("id", identificador).query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:reloj, :paciente, :inicio, :hash) RETURNING id_asignacion""")
				.param("reloj", dispositivo)
				.param("paciente", idPaciente)
				.param("inicio", Timestamp.from(ahora.minus(1, ChronoUnit.DAYS)))
				.param("hash", secretos.hash(token))
				.query(UUID.class).single();
	}

}
