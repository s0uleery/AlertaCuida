package cl.ucn.alertacuida.soporte;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Datos y mensajes comunes de las pruebas del escalamiento de punta a punta: Rosa con su reloj, la hija
 * (prioridad 1) y el vecino (prioridad 2) en su lista, ambos con correo, y un coordinador activo que recibe
 * los avisos del vigilante. Los avisos van por correo porque WhatsApp aún no tiene canal.
 */
public final class EscenarioEscalamiento {

	/** Token con que se identifica el reloj de Rosa. */
	public static final String TOKEN = "token-reloj-escalamiento";

	/** Lunes 9 de noviembre de 2026 a mediodía, hora de Chile: hábil según calendario.yml. */
	public static final Instant LUNES_MEDIODIA = LocalDate.of(2026, 11, 9).atTime(12, 0)
			.atZone(ZonaHoraria.CHILE).toInstant();

	/** Correo del coordinador de la prueba. */
	public static final String CORREO_COORDINADOR = "coord.escalamiento@muni.cl";

	/** Token del enlace de respuesta del correo: lo que va después de "#". */
	private static final Pattern ENLACE = Pattern.compile("/responder#([A-Za-z0-9_.-]+)");

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;
	private final SecretosUnSoloUso secretos;

	private UUID hija;
	private UUID vecino;

	/**
	 * @param jdbc     acceso a la base de la aplicación
	 * @param cifrado  cifra los datos personales como lo hace la aplicación
	 * @param secretos calcula el hash del token del reloj
	 */
	public EscenarioEscalamiento(JdbcClient jdbc, CifradoCampos cifrado, SecretosUnSoloUso secretos) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
		this.secretos = secretos;
	}

	/** Deja la base solo con Rosa, su reloj, la hija, el vecino y el coordinador de la prueba. */
	public void preparar() {
		jdbc.sql("DELETE FROM paciente").update();
		jdbc.sql("DELETE FROM contacto").update();
		jdbc.sql("DELETE FROM smartwatch").update();
		jdbc.sql("DELETE FROM usuario WHERE rut_hash LIKE 'hash-escalamiento-%'").update();
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-escalamiento', 'Rosa', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-ESCALAMIENTO', 'OPERATIVO')
				RETURNING id_dispositivo""").query(UUID.class).single();
		jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, :hash)""")
				.param("r", reloj).param("p", paciente)
				.param("inicio", Timestamp.from(LUNES_MEDIODIA.minus(Duration.ofDays(1))))
				.param("hash", secretos.hash(TOKEN))
				.update();
		hija = vinculo(paciente, "Hija", "hija@correo.cl", 1);
		vecino = vinculo(paciente, "Vecino", "vecino@correo.cl", 2);
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, telefono, hash_contrasena,
				activo) VALUES ('COORDINADOR', 'cifrado', 'hash-escalamiento-coord', 'Coordinador', :correo,
				'cifrado', 'clave', true)""")
				.param("correo", CORREO_COORDINADOR)
				.update();
	}

	/** @return el vínculo de la hija con Rosa (prioridad 1) */
	public UUID hija() {
		return hija;
	}

	/** @return el vínculo del vecino con Rosa (prioridad 2) */
	public UUID vecino() {
		return vecino;
	}

	/** @return una caída grave detectada un minuto antes de esa hora y enviada a esa hora */
	public static String alerta(UUID eventId, Instant ahora) {
		return """
				{"eventId": "%s", "tipoEvento": "CAIDA", "severidad": "GRAVE", "versionConfiguracion": "v1",
				"fechaHoraDeteccion": "%s", "fechaHoraEnvio": "%s", "esperaDiaHabil": false,
				"evidencia": {"segundosInmovilidad": 60, "nivelBateria": 70}}"""
				.formatted(eventId, ahora.minus(Duration.ofMinutes(1)), ahora);
	}

	/** @return el cuerpo JSON de una solicitud de aviso por correo */
	public static String solicitud(UUID eventId, UUID contacto, int ronda) {
		return """
				{"eventId": "%s", "idContacto": "%s", "canal": "CORREO", "ronda": %d}"""
				.formatted(eventId, contacto, ronda);
	}

	/** @return el cuerpo JSON de la respuesta del contacto en la página pública */
	public static String respuesta(String token, String respuesta) {
		return "{\"token\":\"" + token + "\",\"respuesta\":\"" + respuesta + "\"}";
	}

	/**
	 * @return el token del enlace de respuesta del correo pendiente a ese destinatario, redactado como sale;
	 *         se llama antes de enviarlo, porque la bandeja solo trae los pendientes
	 */
	public static String tokenDelCorreo(BandejaSalida bandeja, PlantillasCorreo plantillas, Instant ahora,
			String destinatario) {
		CorreoPendiente correo = bandeja.pendientes(ahora).stream()
				.filter(c -> c.destinatario().equals(destinatario)).findFirst()
				.orElseThrow(() -> new AssertionError("No hay un correo pendiente a " + destinatario));
		Matcher enlace = ENLACE.matcher(plantillas.redactar(correo).texto());
		assertThat(enlace.find()).as("el correo lleva el enlace de respuesta").isTrue();
		return enlace.group(1);
	}

	/** @return cuántos avisos pidió el reloj para ese contacto en la alerta */
	public int avisosA(UUID eventAlerta, UUID contacto) {
		return jdbc.sql("""
				SELECT count(*) FROM registro_intento i JOIN alerta a ON a.id_alerta = i.id_alerta
				WHERE a.event_id = :e AND i.id_contacto_paciente = :c""")
				.param("e", eventAlerta).param("c", contacto).query(Integer.class).single();
	}

	/** @return si el aviso ya salió */
	public boolean enviado(UUID idIntento) {
		return jdbc.sql("SELECT fecha_hora_envio IS NOT NULL FROM registro_intento WHERE id_intento = :i")
				.param("i", idIntento).query(Boolean.class).single();
	}

	/** @return cuántos correos del vigilante tiene la alerta para el coordinador de la prueba */
	public int correosDelVigilante(UUID eventAlerta) {
		return jdbc.sql("""
				SELECT count(*) FROM registro_intento i
				JOIN alerta a ON a.id_alerta = i.id_alerta
				JOIN contacto c ON c.id_contacto = i.id_contacto_interno
				JOIN usuario u ON u.id_usuario = c.id_usuario
				WHERE a.event_id = :e AND i.proposito = 'VIGILANTE' AND u.rut_hash = 'hash-escalamiento-coord'""")
				.param("e", eventAlerta).query(Integer.class).single();
	}

	/** @return cuántos avisos CAMBIO_ESTADO_ALERTA quedaron para el reloj */
	public long avisosConfirmadaAlReloj(UUID eventAlerta) {
		return jdbc.sql("""
				SELECT count(*) FROM aviso_fcm f JOIN alerta a ON a.id_alerta = f.id_alerta
				WHERE a.event_id = :e AND f.tipo = 'CAMBIO_ESTADO_ALERTA'""")
				.param("e", eventAlerta).query(Long.class).single();
	}

	/** @return el contacto interno del coordinador: con ese id lo conoce el reloj si confirma; null si no existe */
	public UUID contactoDelCoordinador() {
		return jdbc.sql("""
				SELECT c.id_contacto FROM contacto c JOIN usuario u ON u.id_usuario = c.id_usuario
				WHERE u.rut_hash = 'hash-escalamiento-coord'""")
				.query(UUID.class).optional().orElse(null);
	}

	/** @return cuántas alertas tiene la base con ese eventId */
	public int alertasCon(UUID eventAlerta) {
		return jdbc.sql("SELECT count(*) FROM alerta WHERE event_id = :e").param("e", eventAlerta)
				.query(Integer.class).single();
	}

	/** @return el vínculo de una persona con correo en la lista de Rosa */
	private UUID vinculo(UUID paciente, String nombre, String correo, int prioridad) {
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, correo, correo_hash) VALUES (:nombre, :correo, :hash)
				RETURNING id_contacto""")
				.param("nombre", cifrado.cifrar(nombre, ColumnaCifrada.CONTACTO_NOMBRE))
				.param("correo", cifrado.cifrar(correo, ColumnaCifrada.CONTACTO_CORREO))
				.param("hash", "hash-c-escalamiento-" + nombre)
				.query(UUID.class).single();
		return BaseDePrueba.vincular(jdbc, paciente, persona, "OTRO", prioridad);
	}

}
