package cl.ucn.alertacuida.reloj;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.escalamiento.VigilarEscalamiento;
import cl.ucn.alertacuida.application.escalamiento.puerto.EscalamientosTrabados;
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
 * Vigilante del escalamiento contra la base real, con el calendario de calendario.yml y un lunes hábil a
 * mediodía. Decisiones del 13-11-2026 (docs/alertas.md). Sin Docker se omite.
 * <ul>
 * <li>R1 Alerta del reloj abierta y 15 minutos sin que el reloj pida avisos: correo a cada coordinador
 * activo, con su texto propio, pendiente de envío.</li>
 * <li>R2 Una sola vez por alerta.</li>
 * <li>R3 Si el reloj pidió un aviso hace menos de 15 minutos, no.</li>
 * <li>R4 No vigila alertas confirmadas, sin respuesta (el reloj ya avisó que terminó), canceladas, en espera
 * de día hábil, simulacros ni técnicas del servidor.</li>
 * </ul>
 */
@SpringBootTest
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosVigilanteEscalamientoTest {

	/** Lunes 9 de noviembre de 2026 a mediodía, hora de Chile: hábil según calendario.yml. */
	private static final Instant AHORA = LocalDate.of(2026, 11, 9).atTime(12, 0).atZone(ZonaHoraria.CHILE)
			.toInstant();

	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private EscalamientosTrabados trabados;
	@Autowired
	private CalendarioHabil calendario;
	@Autowired
	private Transacciones transacciones;
	@Autowired
	private BandejaSalida bandeja;

	private UUID asignacion;
	private UUID hija;

	/** Base vacía; Rosa con su reloj y la hija en su lista; dos coordinadores activos, uno inactivo y Ana. */
	@BeforeEach
	void preparar() {
		jdbc.sql("DELETE FROM paciente").update();
		jdbc.sql("DELETE FROM contacto").update();
		jdbc.sql("DELETE FROM smartwatch").update();
		jdbc.sql("DELETE FROM usuario WHERE rut_hash LIKE 'hash-vigilante-%'").update();
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, alias, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-vigilante', 'Rosa', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-VIGILANTE', 'OPERATIVO')
				RETURNING id_dispositivo""").query(UUID.class).single();
		asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:r, :p, :inicio, 'hash-token-vigilante') RETURNING id_asignacion""")
				.param("r", reloj).param("p", paciente).param("inicio", Timestamp.from(AHORA.minusSeconds(86_400)))
				.query(UUID.class).single();
		UUID persona = jdbc.sql("""
				INSERT INTO contacto (nombre, correo, correo_hash) VALUES (:n, :c, 'hash-c-vigilante')
				RETURNING id_contacto""")
				.param("n", cifrado.cifrar("Hija", ColumnaCifrada.CONTACTO_NOMBRE))
				.param("c", cifrado.cifrar("hija@correo.cl", ColumnaCifrada.CONTACTO_CORREO))
				.query(UUID.class).single();
		hija = jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad)
				VALUES (:p, :c, 'OTRO', 1) RETURNING id_contacto_paciente""")
				.param("p", paciente).param("c", persona).query(UUID.class).single();
		cuenta("COORDINADOR", "coord1", true);
		cuenta("COORDINADOR", "coord2", true);
		cuenta("COORDINADOR", "coord-inactivo", false);
		cuenta("PERSONAL", "ana", true);
	}

	/** R1: a los 20 minutos sin noticias, un correo a cada coordinador activo, con el texto del vigilante. */
	@Test
	void sinNoticiasAvisaACadaCoordinadorActivo() {
		UUID alerta = alerta("'RELOJ'", "'h'", false, false);
		avisoPedido(alerta, AHORA.minus(Duration.ofMinutes(20)));

		vigilar();

		assertThat(correosDelVigilante(alerta)).isEqualTo(2);
		List<CorreoPendiente> pendientes = bandeja.pendientes(AHORA).stream().filter(CorreoPendiente::vigilante)
				.toList();
		assertThat(pendientes).allSatisfy(c -> assertThat(c.alEquipo()).isTrue());
		assertThat(pendientes).extracting(CorreoPendiente::destinatario)
				.contains("coord1@muni.cl", "coord2@muni.cl")
				.doesNotContain("coord-inactivo@muni.cl", "ana@muni.cl");
	}

	/** R2: correr el vigilante otra vez no repite el aviso. */
	@Test
	void avisaUnaSolaVezPorAlerta() {
		UUID alerta = alerta("'RELOJ'", "'h'", false, false);
		avisoPedido(alerta, AHORA.minus(Duration.ofMinutes(20)));

		vigilar();
		vigilar();

		assertThat(correosDelVigilante(alerta)).isEqualTo(2);
	}

	/** R3: si el reloj pidió un aviso hace 10 minutos, el recorrido sigue vivo y no se avisa. */
	@Test
	void conNoticiasRecientesNoAvisa() {
		UUID alerta = alerta("'RELOJ'", "'h'", false, false);
		avisoPedido(alerta, AHORA.minus(Duration.ofMinutes(10)));

		vigilar();

		assertThat(correosDelVigilante(alerta)).isZero();
	}

	/** R1: una alerta sin ningún aviso pedido también se vigila, desde que llegó. */
	@Test
	void sinNingunAvisoSeMideDesdeQueLlego() {
		UUID alerta = alerta("'RELOJ'", "'h'", false, false);

		vigilar();

		assertThat(correosDelVigilante(alerta)).isEqualTo(2);
	}

	/** R4: confirmadas, sin respuesta, canceladas, en espera, simulacros y técnicas del servidor no se vigilan. */
	@Test
	void noVigilaLoQueNoEstaEscalando() {
		UUID confirmada = alerta("'RELOJ'", "'h'", false, false);
		UUID intento = avisoPedido(confirmada, AHORA.minus(Duration.ofMinutes(20)));
		jdbc.sql("UPDATE registro_intento SET respuesta = 'VOY_EN_CAMINO', fecha_hora_respuesta = :t "
				+ "WHERE id_intento = :i").param("t", Timestamp.from(AHORA.minus(Duration.ofMinutes(18))))
				.param("i", intento).update();
		UUID sinRespuesta = alerta("'RELOJ'", "'h'", false, false);
		jdbc.sql("UPDATE alerta SET fecha_hora_sin_respuesta = :t WHERE id_alerta = :a")
				.param("t", Timestamp.from(AHORA.minus(Duration.ofMinutes(16)))).param("a", sinRespuesta).update();
		UUID cancelada = alerta("'RELOJ'", "'h'", false, false);
		jdbc.sql("UPDATE alerta SET fecha_hora_cancelacion = :t WHERE id_alerta = :a")
				.param("t", Timestamp.from(AHORA.minus(Duration.ofMinutes(30)))).param("a", cancelada).update();
		UUID enEspera = alerta("'RELOJ'", "'h'", false, true);
		UUID simulacro = alerta("'RELOJ'", "'h'", true, false);
		UUID tecnica = alerta("'SERVIDOR'", "NULL", false, false);

		vigilar();

		for (UUID alerta : List.of(confirmada, sinRespuesta, cancelada, enEspera, simulacro, tecnica)) {
			assertThat(correosDelVigilante(alerta)).as(alerta.toString()).isZero();
		}
	}

	/** Corre el vigilante con el reloj fijo en el lunes hábil. */
	private void vigilar() {
		new VigilarEscalamiento(trabados, calendario, Duration.ofMinutes(15), Duration.ofHours(1), transacciones,
				Clock.fixed(AHORA, ZoneOffset.UTC)).vigilar();
	}

	/**
	 * @return una alerta de Rosa recibida hace 30 minutos; con origen SERVIDOR es técnica (batería baja) y si
	 *         espera día hábil es leve
	 */
	private UUID alerta(String origen, String hash, boolean simulacro, boolean esperaDiaHabil) {
		String tipo = origen.contains("SERVIDOR") ? "BATERIA_BAJA" : "CAIDA";
		String severidad = esperaDiaHabil || origen.contains("SERVIDOR") ? "LEVE" : "GRAVE";
		return jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				es_simulacro, espera_dia_habil, fecha_hora_deteccion, fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, %s, %s, :tipo, :sev, :sim, :espera, :t, :t) RETURNING id_alerta"""
				.formatted(hash, origen))
				.param("a", asignacion).param("tipo", tipo).param("sev", severidad).param("sim", simulacro)
				.param("espera", esperaDiaHabil).param("t", Timestamp.from(AHORA.minus(Duration.ofMinutes(30))))
				.query(UUID.class).single();
	}

	/** @return un aviso por correo a la hija que el reloj pidió a esa hora, ya enviado */
	private UUID avisoPedido(UUID alerta, Instant solicitud) {
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda,
				fecha_hora_solicitud, fecha_hora_envio) VALUES (gen_random_uuid(), :a, :h, 'CORREO', 1, :s, :s)
				RETURNING id_intento""")
				.param("a", alerta).param("h", hija).param("s", Timestamp.from(solicitud))
				.query(UUID.class).single();
	}

	/**
	 * @return cuántos correos del vigilante tiene la alerta para las cuentas de esta prueba (otras pruebas
	 *         pueden dejar coordinadores en la base)
	 */
	private int correosDelVigilante(UUID alerta) {
		return jdbc.sql("""
				SELECT count(*) FROM registro_intento i
				JOIN contacto c ON c.id_contacto = i.id_contacto_interno
				JOIN usuario u ON u.id_usuario = c.id_usuario
				WHERE i.id_alerta = :a AND i.proposito = 'VIGILANTE' AND i.canal = 'CORREO'
				AND i.fecha_hora_proximo_envio IS NOT NULL AND u.rut_hash LIKE 'hash-vigilante-%'""")
				.param("a", alerta).query(Integer.class).single();
	}

	/** Crea una cuenta del equipo con el correo nombre@muni.cl. */
	private void cuenta(String rol, String nombre, boolean activa) {
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, telefono, hash_contrasena,
				activo) VALUES (:rol, 'cifrado', :hash, :nombre, :correo, 'cifrado', 'clave', :activa)""")
				.param("rol", rol).param("hash", "hash-vigilante-" + nombre).param("nombre", nombre)
				.param("correo", nombre + "@muni.cl").param("activa", activa)
				.update();
	}

}
