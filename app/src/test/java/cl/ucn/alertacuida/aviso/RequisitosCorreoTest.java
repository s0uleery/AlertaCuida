package cl.ucn.alertacuida.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.adapters.correo.CanalCorreoSmtp;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.EncolarCorreos;
import cl.ucn.alertacuida.application.aviso.EnviarCorreosPendientes;
import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import cl.ucn.alertacuida.application.aviso.puerto.ColaCorreos;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.configuracion.PropiedadesCorreo;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Correo institucional contra la base real y un servidor de correo de prueba (GreenMail): no sale
 * ningún correo real. Cada caso sale de un requisito escrito (cronograma, decisiones del 05-11,
 * 08-11 y 10-11-2026). La hora se fija en un lunes hábil para que el resultado no dependa de cuándo
 * corren las pruebas. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 Las cuentas activas con recibe_avisos reciben un correo por cada alerta técnica; las demás
 * cuentas no. Las alertas de salud no van por recibe_avisos: a quién se avisa lo decide el reloj.</li>
 * <li>R2 Solo en horario laboral: fuera de él no se envía nada.</li>
 * <li>R3 No se avisa de simulacros ni de alertas ya resueltas.</li>
 * <li>R4 Un WhatsApp fallido genera su correo de respaldo, al contacto del paciente o a la cuenta del
 * equipo, si tiene correo.</li>
 * <li>R5 Sin datos clínicos; si el paciente no tiene alias, se usa el folio. Sale desde la cuenta
 * institucional.</li>
 * <li>R6 Si el servidor de correo falla: 3 reintentos (1, 5 y 15 minutos) y después queda fallido,
 * con el motivo, visible para el equipo.</li>
 * <li>R7 Un correo al equipo no se repite, y no cambia el estado de contacto de la alerta (el recorrido
 * de contactos es del reloj).</li>
 * <li>R8 Las graves salen marcadas como importantes (Importance: High y X-Priority: 1); las demás, sin
 * marca (decisión del 11-11-2026).</li>
 * </ul>
 */
@SpringBootTest(properties = {"alertacuida.correo.canal=SMTP", "spring.mail.host=localhost",
	"spring.mail.port=3025", "alertacuida.correo.remitente=alertacuida@muni.cl"})
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosCorreoTest {

	/** Servidor SMTP de prueba en el puerto 3025: guarda los correos en memoria. */
	@RegisterExtension
	static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

	/** Lunes 09-11-2026 a mediodía, hora de Chile: horario hábil. */
	private static final Instant MEDIODIA = LocalDate.of(2026, 11, 9).atTime(12, 0).atZone(ZonaHoraria.CHILE)
			.toInstant();
	/** El mismo lunes a las 20:00: fuera del horario hábil. */
	private static final Instant NOCHE = MEDIODIA.plus(Duration.ofHours(8));

	private static final String ANA = "ana@muni.cl";
	private static final String LUIS = "luis@muni.cl";
	private static final String INACTIVA = "inactiva@muni.cl";
	private static final String COORDINADORA = "coordinadora@muni.cl";
	private static final String HIJA = "hija@correo.cl";

	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private ColaCorreos cola;
	@Autowired
	private BandejaSalida bandeja;
	@Autowired
	private CanalNotificacion canal;
	@Autowired
	private PlantillasCorreo plantillas;
	@Autowired
	private PoliticaReintentos politica;
	@Autowired
	private CalendarioHabil calendario;
	@Autowired
	private PropiedadesCorreo propiedades;
	@Autowired
	private Transacciones transacciones;

	private UUID asignacion;
	private UUID idAna;
	private UUID vinculoHija;
	private UUID vinculoVecino;

	/**
	 * Base vacía; un paciente con alias "Rosa" y su reloj; Ana (PERSONAL, activa, con recibe_avisos), Luis y
	 * una coordinadora (sin recibe_avisos) y una cuenta inactiva con recibe_avisos; la hija con correo y el
	 * vecino solo con WhatsApp en la lista de Rosa.
	 */
	@BeforeEach
	void preparar() {
		jdbc.sql("DELETE FROM paciente").update();
		jdbc.sql("DELETE FROM contacto").update();
		jdbc.sql("DELETE FROM smartwatch").update();
		jdbc.sql("DELETE FROM usuario WHERE rut_hash LIKE 'hash-correo-%'").update();
		UUID paciente = paciente("Rosa");
		asignacion = asignacion(paciente, "R-CORREO");
		idAna = cuenta("ana", "PERSONAL", ANA, true, true);
		cuenta("luis", "COORDINADOR", LUIS, true, false);
		cuenta("coordinadora", "COORDINADOR", COORDINADORA, true, false);
		cuenta("inactiva", "PERSONAL", INACTIVA, false, true);
		vinculoHija = vinculo(paciente, "Hija", HIJA, 1);
		vinculoVecino = vinculo(paciente, "Vecino", null, 2);
	}

	/**
	 * R1: una alerta técnica del horario hábil llega por correo a cada cuenta activa con recibe_avisos, de
	 * cualquier rol: a la coordinadora también, además de su WhatsApp (decisión del 10-11-2026).
	 */
	@Test
	void unaTecnicaLlegaSoloALasCuentasConRecibeAvisos() throws Exception {
		jdbc.sql("UPDATE usuario SET recibe_avisos = true WHERE correo = :c").param("c", COORDINADORA).update();
		alertaServidor("BATERIA_BAJA", "LEVE", MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));

		assertThat(destinatarios()).containsExactlyInAnyOrder(ANA, COORDINADORA);
	}

	/** R1: las cuatro técnicas llegan, cada una con su correo. */
	@Test
	void todasLasTecnicasLlegan() throws Exception {
		alertaServidor("RELOJ_NO_PUESTO", "LEVE", MEDIODIA);
		alertaServidor("EXCESO_ALERTAS", "LEVE", MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));

		assertThat(destinatarios()).containsExactly(ANA, ANA);
	}

	/** R1: un silencio de datos (media) también llega, con su propia plantilla. */
	@Test
	void unSilencioDeDatosLlega() throws Exception {
		alertaServidor("SILENCIO_DATOS", "MEDIA", MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));

		assertThat(destinatarios()).containsExactly(ANA);
		assertThat(SMTP.getReceivedMessages()[0].getSubject()).contains("dejó de sincronizar");
	}

	/**
	 * R1: las alertas de salud, de cualquier gravedad y aunque se agraven, no van por recibe_avisos: a
	 * quién se avisa lo decide el reloj.
	 */
	@Test
	void lasDeSaludNoLleganPorRecibeAvisos() throws Exception {
		alertaReloj("CAIDA", "GRAVE", MEDIODIA, "");
		alertaReloj("FC", "MEDIA", MEDIODIA, "");
		alertaReloj("BOTON_AYUDA", "LEVE", MEDIODIA, "");
		alertaReloj("CAIDA", "LEVE", MEDIODIA, ", fecha_hora_agravamiento = fecha_hora_recepcion");

		avisar(MEDIODIA.plusSeconds(30));

		assertThat(destinatarios()).isEmpty();
	}

	/** R2: una técnica fuera de horario no envía nada, ni siquiera cuando empieza la jornada siguiente. */
	@Test
	void fueraDeHorarioNoSeEnviaNada() throws Exception {
		alertaServidor("SILENCIO_DATOS", "MEDIA", NOCHE);

		avisar(NOCHE.plusSeconds(30));
		avisar(MEDIODIA.plus(Duration.ofDays(1)).minus(Duration.ofHours(3)));

		assertThat(destinatarios()).isEmpty();
	}

	/** R3: no se avisa de simulacros ni de alertas ya resueltas. */
	@Test
	void noSeAvisaDeSimulacrosNiResueltas() throws Exception {
		UUID simulacro = alertaServidor("BATERIA_BAJA", "LEVE", MEDIODIA);
		jdbc.sql("UPDATE alerta SET es_simulacro = true WHERE id_alerta = :a").param("a", simulacro).update();
		UUID resuelta = alertaServidor("RELOJ_NO_PUESTO", "LEVE", MEDIODIA);
		jdbc.sql("INSERT INTO atencion_alerta (id_alerta, fecha_hora_cierre) VALUES (:a, :t)")
				.param("a", resuelta).param("t", Timestamp.from(MEDIODIA)).update();

		avisar(MEDIODIA.plusSeconds(30));

		assertThat(destinatarios()).isEmpty();
	}

	/** R4: un WhatsApp fallido a la hija genera su correo de respaldo; al vecino, sin correo, no. */
	@Test
	void unWhatsappFallidoGeneraSuRespaldo() throws Exception {
		UUID alerta = alertaReloj("FC", "MEDIA", NOCHE, "");
		whatsappFallido(alerta, vinculoHija, null, NOCHE);
		whatsappFallido(alerta, vinculoVecino, null, NOCHE);

		avisar(NOCHE.plusSeconds(30));

		assertThat(destinatarios()).containsExactly(HIJA);
		assertThat(jdbc.sql("""
				SELECT ronda FROM registro_intento WHERE canal = 'CORREO' AND id_contacto_paciente = :v""")
				.param("v", vinculoHija).query(Integer.class).single()).isEqualTo(1);
	}

	/**
	 * R4: el WhatsApp fallido a una cuenta del equipo (aviso a la oficina PRLAC, que va por todas las
	 * alertas) genera el correo a su cuenta. Ana no recibe nada: la alerta es de salud.
	 */
	@Test
	void unWhatsappFallidoAlEquipoGeneraElCorreoASuCuenta() throws Exception {
		UUID alerta = alertaReloj("FC", "MEDIA", MEDIODIA, "");
		UUID luis = jdbc.sql("SELECT id_usuario FROM usuario WHERE correo = :c").param("c", LUIS)
				.query(UUID.class).single();
		UUID interno = jdbc.sql("INSERT INTO contacto (id_usuario) VALUES (:u) RETURNING id_contacto")
				.param("u", luis).query(UUID.class).single();
		whatsappFallido(alerta, null, interno, MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));

		assertThat(destinatarios()).containsExactly(LUIS);
		assertThat(SMTP.getReceivedMessages()[0].getSubject()).contains("alerta media", "Rosa");
	}

	/** R5: el respaldo sale desde la cuenta institucional, con el alias y el folio, sin la evidencia clínica. */
	@Test
	void elCorreoNoLlevaDatosClinicos() throws Exception {
		UUID alerta = alertaReloj("FC", "GRAVE", MEDIODIA, ", evidencia = '{\"lecturas\": [{\"valor\": 142}]}'");
		long folio = jdbc.sql("SELECT numero FROM alerta").query(Long.class).single();
		whatsappFallido(alerta, vinculoHija, null, MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));

		MimeMessage correo = SMTP.getReceivedMessages()[0];
		String texto = correo.getSubject() + correo.getContent();
		assertThat(((InternetAddress) correo.getFrom()[0]).getAddress()).isEqualTo("alertacuida@muni.cl");
		assertThat(texto).contains("Rosa", "#" + folio).doesNotContain("142 ", "lecturas", "FC");
	}

	/** R8: el respaldo de una grave sale marcado como importante. */
	@Test
	void unaGraveSaleComoImportante() throws Exception {
		UUID alerta = alertaReloj("CAIDA", "GRAVE", MEDIODIA, "");
		whatsappFallido(alerta, vinculoHija, null, MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));

		MimeMessage correo = SMTP.getReceivedMessages()[0];
		assertThat(correo.getHeader("Importance")).containsExactly("High");
		assertThat(correo.getHeader("X-Priority")).containsExactly("1");
	}

	/** R8: el respaldo de una media sale sin marca de importancia. */
	@Test
	void unaMediaSaleSinMarca() throws Exception {
		UUID alerta = alertaReloj("CAIDA", "MEDIA", MEDIODIA, "");
		whatsappFallido(alerta, vinculoHija, null, MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));

		MimeMessage correo = SMTP.getReceivedMessages()[0];
		assertThat(correo.getHeader("Importance")).isNull();
		assertThat(correo.getHeader("X-Priority")).isNull();
	}

	/** R5: sin alias, el correo usa el folio de la alerta. */
	@Test
	void sinAliasSeUsaElFolio() throws Exception {
		jdbc.sql("UPDATE paciente SET alias = NULL").update();
		alertaServidor("BATERIA_BAJA", "LEVE", MEDIODIA);
		long folio = jdbc.sql("SELECT numero FROM alerta").query(Long.class).single();

		avisar(MEDIODIA.plusSeconds(30));

		assertThat(SMTP.getReceivedMessages()[0].getSubject()).contains("#" + folio).doesNotContain("null");
	}

	/** R6: con el servidor de correo caído, reintenta a los 1, 5 y 15 minutos y después queda fallido. */
	@Test
	void conElServidorCaidoReintentaYQuedaFallido() {
		alertaServidor("BATERIA_BAJA", "LEVE", MEDIODIA);
		JavaMailSenderImpl caido = new JavaMailSenderImpl();
		caido.setHost("localhost");
		caido.setPort(1);
		CanalNotificacion sinServidor = new CanalCorreoSmtp(caido, propiedades.remitente());

		Instant primero = MEDIODIA.plusSeconds(30);
		encolar(primero);
		enviarCon(sinServidor, primero);
		assertThat(proximoEnvio()).isEqualTo(primero.plus(Duration.ofMinutes(1)));
		enviarCon(sinServidor, primero.plus(Duration.ofMinutes(1)));
		assertThat(proximoEnvio()).isEqualTo(primero.plus(Duration.ofMinutes(6)));
		enviarCon(sinServidor, primero.plus(Duration.ofMinutes(6)));
		assertThat(proximoEnvio()).isEqualTo(primero.plus(Duration.ofMinutes(21)));
		enviarCon(sinServidor, primero.plus(Duration.ofMinutes(21)));

		assertThat(jdbc.sql("""
				SELECT reintentos || '|' || (fecha_hora_fallo IS NOT NULL) || '|' || (motivo_fallo IS NOT NULL)
				|| '|' || (fecha_hora_proximo_envio IS NULL) FROM registro_intento""")
				.query(String.class).single()).isEqualTo("3|true|true|true");
	}

	/** R6: un reintento no se envía antes de su hora. */
	@Test
	void unReintentoEsperaSuHora() throws Exception {
		alertaServidor("BATERIA_BAJA", "LEVE", MEDIODIA);
		encolar(MEDIODIA);
		jdbc.sql("UPDATE registro_intento SET reintentos = 1, fecha_hora_proximo_envio = :t")
				.param("t", Timestamp.from(MEDIODIA.plus(Duration.ofMinutes(5)))).update();

		enviarCon(canal, MEDIODIA.plus(Duration.ofMinutes(4)));
		assertThat(destinatarios()).isEmpty();

		enviarCon(canal, MEDIODIA.plus(Duration.ofMinutes(5)));
		assertThat(destinatarios()).containsExactly(ANA);
	}

	/** R7: varias pasadas envían un solo correo por cuenta y alerta, y queda registrado como enviado. */
	@Test
	void variasPasadasEnvianUnSoloCorreo() throws Exception {
		alertaServidor("BATERIA_BAJA", "LEVE", MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));
		avisar(MEDIODIA.plusSeconds(60));
		avisar(MEDIODIA.plusSeconds(90));

		assertThat(destinatarios()).containsExactly(ANA);
		assertThat(jdbc.sql("""
				SELECT (fecha_hora_envio IS NOT NULL) || '|' || (id_externo IS NOT NULL) FROM registro_intento i
				JOIN contacto c ON c.id_contacto = i.id_contacto_interno WHERE c.id_usuario = :u""")
				.param("u", idAna).query(String.class).single()).isEqualTo("true|true");
	}

	/** R7: el correo al equipo no cuenta como aviso a los contactos del paciente. */
	@Test
	void elCorreoAlEquipoNoCambiaElEstadoDeContacto() {
		UUID alerta = alertaServidor("BATERIA_BAJA", "LEVE", MEDIODIA);

		avisar(MEDIODIA.plusSeconds(30));

		assertThat(jdbc.sql("SELECT estado_contacto FROM alerta_estado WHERE id_alerta = :a").param("a", alerta)
				.query(String.class).single()).isEqualTo("RECIBIDA");
	}

	// ---------- Ayudantes ----------

	/** Encola y envía, como la tarea programada, a esa hora. */
	private void avisar(Instant ahora) {
		encolar(ahora);
		enviarCon(canal, ahora);
	}

	/** Encola a esa hora, con la ventana de application.properties. */
	private void encolar(Instant ahora) {
		new EncolarCorreos(cola, calendario, propiedades.ventana(), transacciones, Clock.fixed(ahora, ZoneOffset.UTC))
				.encolar();
	}

	/** Envía los pendientes a esa hora por ese canal. */
	private void enviarCon(CanalNotificacion conCanal, Instant ahora) {
		new EnviarCorreosPendientes(bandeja, conCanal, plantillas, politica, Clock.fixed(ahora, ZoneOffset.UTC))
				.enviar();
	}

	/** @return a quiénes llegó cada correo, en orden */
	private static List<String> destinatarios() throws Exception {
		return Arrays.stream(SMTP.getReceivedMessages())
				.map(RequisitosCorreoTest::destinatario)
				.toList();
	}

	/** @return el único destinatario de un correo */
	private static String destinatario(MimeMessage correo) {
		try {
			return ((InternetAddress) correo.getRecipients(Message.RecipientType.TO)[0]).getAddress();
		} catch (jakarta.mail.MessagingException e) {
			throw new IllegalStateException(e);
		}
	}

	/** @return cuándo toca el próximo intento del único correo */
	private Instant proximoEnvio() {
		return jdbc.sql("SELECT fecha_hora_proximo_envio FROM registro_intento").query(Timestamp.class).single()
				.toInstant();
	}

	/** @return un paciente nuevo con ese alias */
	private UUID paciente(String alias) {
		return jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-correo', :alias, CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.param("alias", alias)
				.query(UUID.class).single();
	}

	/** @return el vínculo vigente de un reloj nuevo con el paciente */
	private UUID asignacion(UUID paciente, String identificador) {
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES (:id, 'OPERATIVO')
				RETURNING id_dispositivo""")
				.param("id", identificador)
				.query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:reloj, :paciente, :inicio, 'hash-token-correo') RETURNING id_asignacion""")
				.param("reloj", reloj)
				.param("paciente", paciente)
				.param("inicio", Timestamp.from(MEDIODIA.minus(Duration.ofDays(1))))
				.query(UUID.class).single();
	}

	/** @return una cuenta del equipo con ese rol */
	private UUID cuenta(String nombre, String rol, String correo, boolean activa, boolean recibeAvisos) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo,
				recibe_avisos) VALUES (:rol, 'cifrado', :hash, :nombre, :correo, 'clave', :activa, :recibe)
				RETURNING id_usuario""")
				.param("rol", rol)
				.param("hash", "hash-correo-" + nombre)
				.param("nombre", nombre)
				.param("correo", correo)
				.param("activa", activa)
				.param("recibe", recibeAvisos)
				.query(UUID.class).single();
	}

	/**
	 * @param correo el correo de la persona, o null si solo tiene WhatsApp
	 * @return el vínculo de una persona nueva con el paciente
	 */
	private UUID vinculo(UUID paciente, String nombre, String correo, int prioridad) {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, whatsapp, whatsapp_hash, correo, correo_hash)
				VALUES (:nombre, :whatsapp, :hashW, :correo, :hashC) RETURNING id_contacto""")
				.param("nombre", cifrado.cifrar(nombre, ColumnaCifrada.CONTACTO_NOMBRE))
				.param("whatsapp", cifrado.cifrar("+5691234567" + prioridad, ColumnaCifrada.CONTACTO_WHATSAPP))
				.param("hashW", "hash-w-" + nombre)
				.param("correo", correo == null ? null : cifrado.cifrar(correo, ColumnaCifrada.CONTACTO_CORREO))
				.param("hashC", correo == null ? null : "hash-c-" + nombre)
				.query(UUID.class).single();
		return jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad)
				VALUES (:paciente, :persona, 'OTRO', :prioridad) RETURNING id_contacto_paciente""")
				.param("paciente", paciente)
				.param("persona", persona)
				.param("prioridad", prioridad)
				.query(UUID.class).single();
	}

	/**
	 * @param extra asignaciones extra para la alerta recién creada, empezando con coma, o ""
	 * @return una alerta del reloj recibida a esa hora
	 */
	private UUID alertaReloj(String tipo, String severidad, Instant recepcion, String extra) {
		UUID alerta = jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				fecha_hora_recepcion) VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', :tipo, :sev, :t)
				RETURNING id_alerta""")
				.param("a", asignacion).param("tipo", tipo).param("sev", severidad)
				.param("t", Timestamp.from(recepcion))
				.query(UUID.class).single();
		if (!extra.isEmpty()) {
			jdbc.sql("UPDATE alerta SET " + extra.substring(1) + " WHERE id_alerta = :a").param("a", alerta).update();
		}
		return alerta;
	}

	/** @return una alerta técnica del servidor creada a esa hora */
	private UUID alertaServidor(String tipo, String severidad, Instant recepcion) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, origen, tipo_evento, severidad, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'SERVIDOR', :tipo, :sev, :t) RETURNING id_alerta""")
				.param("a", asignacion).param("tipo", tipo).param("sev", severidad)
				.param("t", Timestamp.from(recepcion))
				.query(UUID.class).single();
	}

	/** Un WhatsApp que Meta informó como fallido, a un vínculo (ronda 1) o a un contacto interno. */
	private void whatsappFallido(UUID alerta, UUID vinculo, UUID interno, Instant fallo) {
		jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, id_contacto_interno, canal,
				ronda, fecha_hora_fallo, motivo_fallo)
				VALUES (gen_random_uuid(), :a, :v, :i, 'WHATSAPP', :ronda, :t, 'Número inválido')""")
				.param("a", alerta).param("v", vinculo).param("i", interno)
				.param("ronda", vinculo == null ? null : 1)
				.param("t", Timestamp.from(fallo))
				.update();
	}

}
