package cl.ucn.alertacuida.reloj;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.adapters.whatsapp.CanalWhatsappNoConfigurado;
import cl.ucn.alertacuida.application.aviso.EncolarCorreos;
import cl.ucn.alertacuida.application.aviso.EnviarWhatsappPendientes;
import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.ColaCorreos;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.escalamiento.AvisarOficina;
import cl.ucn.alertacuida.application.escalamiento.puerto.AvisosOficina;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Aviso a la oficina PRLAC contra la base real, con el calendario de calendario.yml y un lunes hábil a
 * mediodía. Decisiones del 05-11, 08-11 y 13-11-2026 (docs/alertas.md). Sin Docker se omite.
 * <ul>
 * <li>R1 Alerta real en horario laboral: WhatsApp a cada cuenta activa COORDINADOR con teléfono y opt-in
 * vigente. No a cuentas inactivas ni de otros roles.</li>
 * <li>R2 Un coordinador activo sin opt-in vigente (nunca lo dio o lo revocó) recibe un correo en su lugar.</li>
 * <li>R3 Vale para todas las gravedades y también para las alertas técnicas.</li>
 * <li>R4 Una sola vez por alerta y coordinador.</li>
 * <li>R5 No se avisa de simulacros, alertas canceladas o resueltas, ni de alertas recibidas fuera del horario
 * laboral.</li>
 * <li>R6 Si la alerta pasa a grave después del aviso, y eso ocurre en horario laboral, se avisa de nuevo por el
 * mismo canal; el correo dice que pasó a grave, sin nombrar qué se detectó.</li>
 * <li>R7 Si ya era grave cuando salió el primer aviso, no se repite.</li>
 * <li>R8 Si el WhatsApp falla, sale un correo de respaldo con el mismo propósito; un coordinador con
 * recibe_avisos no recibe dos correos por la misma alerta técnica.</li>
 * <li>R9 La bandeja de WhatsApp entrega el número de la cuenta ya descifrado.</li>
 * </ul>
 */
@SpringBootTest
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosAvisoOficinaTest {

	/** Lunes 9 de noviembre de 2026 a mediodía, hora de Chile: hábil según calendario.yml. */
	private static final Instant AHORA = LocalDate.of(2026, 11, 9).atTime(12, 0).atZone(ZonaHoraria.CHILE)
			.toInstant();
	private static final Duration VENTANA = Duration.ofHours(1);
	private static final String TELEFONO = "+56911112222";

	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private AvisosOficina avisos;
	@Autowired
	private CalendarioHabil calendario;
	@Autowired
	private Transacciones transacciones;
	@Autowired
	private BandejaWhatsapp bandejaWhatsapp;
	@Autowired
	private BandejaSalida bandejaCorreos;
	@Autowired
	private ColaCorreos colaCorreos;
	@Autowired
	private PlantillasCorreo plantillas;

	private UUID asignacion;

	/**
	 * Base vacía; Rosa con su reloj. Cuentas: un coordinador con WhatsApp, uno sin opt-in, uno que lo revocó,
	 * uno inactivo y Ana, de otro rol, con opt-in.
	 */
	@BeforeEach
	void preparar() {
		jdbc.sql("DELETE FROM paciente").update();
		jdbc.sql("DELETE FROM contacto").update();
		jdbc.sql("DELETE FROM smartwatch").update();
		jdbc.sql("DELETE FROM usuario WHERE rut_hash LIKE 'hash-oficina-%'").update();
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-oficina', 'Rosa', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-OFICINA', 'OPERATIVO')
				RETURNING id_dispositivo""").query(UUID.class).single();
		asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, 'hash-token-oficina') RETURNING id_asignacion""")
				.param("r", reloj).param("p", paciente).param("inicio", Timestamp.from(AHORA.minusSeconds(86_400)))
				.query(UUID.class).single();
		cuenta("COORDINADOR", "whatsapp", true, true, false);
		cuenta("COORDINADOR", "sin-optin", true, false, false);
		cuenta("COORDINADOR", "revocado", true, true, true);
		cuenta("COORDINADOR", "inactivo", false, true, false);
		cuenta("PERSONAL", "ana", true, true, false);
	}

	/** R1 y R2: WhatsApp al coordinador con opt-in y correo a los que no lo tienen vigente; nada a los demás. */
	@Test
	void avisaACadaCoordinadorPorSuCanal() {
		UUID alerta = alerta("CAIDA", "MEDIA", false, AHORA.minus(Duration.ofMinutes(5)));

		avisar(AHORA);

		assertThat(avisos(alerta, "WHATSAPP", "AVISO")).containsExactly("whatsapp");
		assertThat(avisos(alerta, "CORREO", "AVISO")).containsExactlyInAnyOrder("sin-optin", "revocado");
	}

	/** R3: una leve y una técnica también se avisan. */
	@Test
	void avisaTodasLasGravedadesYLasTecnicas() {
		UUID leve = alerta("FC", "LEVE", false, AHORA.minus(Duration.ofMinutes(5)));
		UUID tecnica = alertaTecnica(AHORA.minus(Duration.ofMinutes(5)));

		avisar(AHORA);

		assertThat(avisos(leve, "WHATSAPP", "AVISO")).containsExactly("whatsapp");
		assertThat(avisos(tecnica, "WHATSAPP", "AVISO")).containsExactly("whatsapp");
		assertThat(avisos(tecnica, "CORREO", "AVISO")).containsExactlyInAnyOrder("sin-optin", "revocado");
	}

	/** R4: otra pasada no repite los avisos. */
	@Test
	void avisaUnaSolaVez() {
		UUID alerta = alerta("CAIDA", "GRAVE", false, AHORA.minus(Duration.ofMinutes(5)));

		avisar(AHORA.minus(Duration.ofMinutes(1)));
		avisar(AHORA);

		assertThat(avisos(alerta, "WHATSAPP", "AVISO")).hasSize(1);
		assertThat(avisos(alerta, "CORREO", "AVISO")).hasSize(2);
	}

	/** R5: simulacros, canceladas, resueltas y recibidas antes de la jornada no se avisan. */
	@Test
	void noAvisaLoQueNoCorresponde() {
		UUID simulacro = alerta("CAIDA", "GRAVE", true, AHORA.minus(Duration.ofMinutes(5)));
		UUID cancelada = alerta("CAIDA", "GRAVE", false, AHORA.minus(Duration.ofMinutes(5)));
		jdbc.sql("UPDATE alerta SET fecha_hora_cancelacion = fecha_hora_recepcion WHERE id_alerta = :a")
				.param("a", cancelada).update();
		UUID resuelta = alerta("CAIDA", "GRAVE", false, AHORA.minus(Duration.ofMinutes(5)));
		jdbc.sql("INSERT INTO atencion_alerta (id_alerta, fecha_hora_cierre) VALUES (:a, :t)")
				.param("a", resuelta).param("t", Timestamp.from(AHORA.minus(Duration.ofMinutes(2)))).update();
		Instant temprano = LocalDate.of(2026, 11, 9).atTime(8, 0).atZone(ZonaHoraria.CHILE).toInstant();
		UUID antesDeLaJornada = alerta("CAIDA", "GRAVE", false, temprano);

		avisar(temprano.plus(Duration.ofMinutes(50)));
		avisar(AHORA);

		for (UUID alerta : List.of(simulacro, cancelada, resuelta, antesDeLaJornada)) {
			assertThat(avisos(alerta, "WHATSAPP", "AVISO")).as(alerta.toString()).isEmpty();
			assertThat(avisos(alerta, "CORREO", "AVISO")).as(alerta.toString()).isEmpty();
		}
	}

	/** R6: una caída media que se agrava después del aviso se avisa de nuevo, con su texto propio. */
	@Test
	void siSeAgravaDespuesDelAvisoSeAvisaDeNuevo() {
		UUID alerta = alerta("CAIDA", "MEDIA", false, AHORA.minus(Duration.ofMinutes(30)));
		avisar(AHORA.minus(Duration.ofMinutes(25)));
		agravar(alerta, AHORA.minus(Duration.ofMinutes(10)));

		avisar(AHORA);
		avisar(AHORA.plusSeconds(15));

		assertThat(avisos(alerta, "WHATSAPP", "AGRAVAMIENTO")).containsExactly("whatsapp");
		assertThat(avisos(alerta, "CORREO", "AGRAVAMIENTO")).containsExactlyInAnyOrder("sin-optin", "revocado");
		CorreoPendiente correo = bandejaCorreos.pendientes(AHORA).stream()
				.filter(c -> c.proposito() == Proposito.AGRAVAMIENTO).findFirst().orElseThrow();
		Mensaje mensaje = plantillas.redactar(correo);
		assertThat(mensaje.asunto()).contains("pasó a grave").contains("Rosa");
		assertThat(mensaje.asunto() + mensaje.texto()).doesNotContainIgnoringCase("caída");
		assertThat(mensaje.importante()).isTrue();
	}

	/** R6: si se agravó fuera del horario laboral, no se avisa de nuevo. */
	@Test
	void siSeAgravaFueraDeHorarioNoSeAvisaDeNuevo() {
		Instant tarde = LocalDate.of(2026, 11, 9).atTime(17, 0).atZone(ZonaHoraria.CHILE).toInstant();
		UUID alerta = alerta("CAIDA", "MEDIA", false, tarde);
		avisar(tarde.plusSeconds(30));
		agravar(alerta, tarde.plus(Duration.ofMinutes(50)));

		avisar(tarde.plus(Duration.ofMinutes(55)));

		assertThat(avisos(alerta, "WHATSAPP", "AVISO")).containsExactly("whatsapp");
		assertThat(avisos(alerta, "WHATSAPP", "AGRAVAMIENTO")).isEmpty();
	}

	/** R7: si se agravó antes del primer aviso, ese aviso ya dice grave y no se repite. */
	@Test
	void siYaEraGraveAlAvisarNoSeRepite() {
		UUID alerta = alerta("CAIDA", "MEDIA", false, AHORA.minus(Duration.ofMinutes(10)));
		agravar(alerta, AHORA.minus(Duration.ofMinutes(5)));

		avisar(AHORA);
		avisar(AHORA.plusSeconds(15));

		assertThat(avisos(alerta, "WHATSAPP", "AVISO")).containsExactly("whatsapp");
		assertThat(avisos(alerta, "WHATSAPP", "AGRAVAMIENTO")).isEmpty();
		assertThat(avisos(alerta, "CORREO", "AGRAVAMIENTO")).isEmpty();
	}

	/**
	 * R8: sin canal de WhatsApp, el envío falla y sale el correo de respaldo con el mismo propósito; con
	 * recibe_avisos, el coordinador recibe un solo correo por la alerta técnica.
	 */
	@Test
	void siElWhatsappFallaSaleElCorreoDeRespaldo() {
		jdbc.sql("UPDATE usuario SET recibe_avisos = true WHERE rut_hash = 'hash-oficina-whatsapp'").update();
		UUID tecnica = alertaTecnica(AHORA.minus(Duration.ofMinutes(5)));
		UUID caida = alerta("CAIDA", "MEDIA", false, AHORA.minus(Duration.ofMinutes(30)));
		avisar(AHORA.minus(Duration.ofMinutes(25)));
		agravar(caida, AHORA.minus(Duration.ofMinutes(10)));
		avisar(AHORA);

		encolarCorreos();
		new EnviarWhatsappPendientes(bandejaWhatsapp, bandejaCorreos, new CanalWhatsappNoConfigurado(),
				new PoliticaReintentos(List.of()), Clock.fixed(AHORA, ZoneOffset.UTC)).enviar();
		encolarCorreos();

		assertThat(avisos(tecnica, "CORREO", "AVISO")).containsExactlyInAnyOrder("whatsapp", "sin-optin",
				"revocado");
		assertThat(avisos(caida, "CORREO", "AVISO")).contains("whatsapp");
		assertThat(avisos(caida, "CORREO", "AGRAVAMIENTO")).contains("whatsapp");
	}

	/** R9: el WhatsApp a la cuenta sale con su número descifrado y marcado como aviso al equipo. */
	@Test
	void laBandejaEntregaElNumeroDeLaCuenta() {
		UUID alerta = alerta("CAIDA", "GRAVE", false, AHORA.minus(Duration.ofMinutes(5)));
		long numero = jdbc.sql("SELECT numero FROM alerta WHERE id_alerta = :a").param("a", alerta)
				.query(Long.class).single();

		avisar(AHORA);

		List<WhatsappPendiente> pendientes = bandejaWhatsapp.pendientes(AHORA).stream()
				.filter(w -> w.numero() == numero).toList();
		assertThat(pendientes).singleElement().satisfies(w -> {
			assertThat(w.telefono()).isEqualTo(TELEFONO);
			assertThat(w.alEquipo()).isTrue();
			assertThat(w.proposito()).isEqualTo(Proposito.AVISO);
			assertThat(w.alias()).isEqualTo("Rosa");
		});
	}

	/** Corre el aviso a la oficina con el reloj fijo en ese momento. */
	private void avisar(Instant ahora) {
		new AvisarOficina(avisos, calendario, VENTANA, transacciones, Clock.fixed(ahora, ZoneOffset.UTC)).avisar();
	}

	/** Encola los correos (recibe_avisos y respaldos) con el reloj fijo en el lunes hábil. */
	private void encolarCorreos() {
		new EncolarCorreos(colaCorreos, calendario, VENTANA, transacciones, Clock.fixed(AHORA, ZoneOffset.UTC))
				.encolar();
	}

	/** @return una alerta de Rosa enviada por el reloj, recibida a esa hora */
	private UUID alerta(String tipo, String severidad, boolean simulacro, Instant recepcion) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				es_simulacro, fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'h', 'RELOJ', :tipo, :sev, :sim, :t, :t) RETURNING id_alerta""")
				.param("a", asignacion).param("tipo", tipo).param("sev", severidad).param("sim", simulacro)
				.param("t", Timestamp.from(recepcion))
				.query(UUID.class).single();
	}

	/** @return una alerta técnica (batería baja) del reloj de Rosa, creada por el servidor a esa hora */
	private UUID alertaTecnica(Instant recepcion) {
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, origen, tipo_evento, severidad, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'SERVIDOR', 'BATERIA_BAJA', 'LEVE', :t) RETURNING id_alerta""")
				.param("a", asignacion).param("t", Timestamp.from(recepcion))
				.query(UUID.class).single();
	}

	/** La caída pasa a grave a esa hora. */
	private void agravar(UUID alerta, Instant cuando) {
		jdbc.sql("UPDATE alerta SET fecha_hora_agravamiento = :t WHERE id_alerta = :a")
				.param("t", Timestamp.from(cuando)).param("a", alerta).update();
	}

	/** @return a qué cuentas de esta prueba (por nombre) se encoló un aviso de la alerta por ese canal */
	private List<String> avisos(UUID alerta, String canal, String proposito) {
		return jdbc.sql("""
				SELECT u.nombre_profesional FROM registro_intento i
				JOIN contacto c ON c.id_contacto = i.id_contacto_interno
				JOIN usuario u ON u.id_usuario = c.id_usuario
				WHERE i.id_alerta = :a AND i.canal = :canal AND i.proposito = :proposito
				AND u.rut_hash LIKE 'hash-oficina-%'""")
				.param("a", alerta).param("canal", canal).param("proposito", proposito)
				.query(String.class).list();
	}

	/**
	 * Crea una cuenta del equipo con el correo nombre@oficina.muni.cl (único entre las pruebas) y el teléfono
	 * de prueba.
	 *
	 * @param optIn    si dio el opt-in de WhatsApp
	 * @param revocado si después lo revocó
	 */
	private void cuenta(String rol, String nombre, boolean activa, boolean optIn, boolean revocado) {
		Timestamp antes = Timestamp.from(AHORA.minusSeconds(86_400));
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, telefono, hash_contrasena,
				activo, fecha_opt_in, fecha_revocacion_opt_in)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :telefono, 'clave', :activa, :optIn, :revocacion)""")
				.param("rol", rol).param("hash", "hash-oficina-" + nombre).param("nombre", nombre)
				.param("correo", nombre + "@oficina.muni.cl")
				.param("telefono", cifrado.cifrar(TELEFONO, ColumnaCifrada.USUARIO_TELEFONO))
				.param("activa", activa)
				.param("optIn", optIn ? antes : null)
				.param("revocacion", revocado ? antes : null)
				.update();
	}

}
