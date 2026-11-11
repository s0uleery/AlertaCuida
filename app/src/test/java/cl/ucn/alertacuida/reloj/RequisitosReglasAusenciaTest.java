package cl.ucn.alertacuida.reloj;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.application.calendario.ReglaEnrutamiento;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.vigilancia.ReglasAusencia;
import cl.ucn.alertacuida.application.vigilancia.VigilarRelojes;
import cl.ucn.alertacuida.application.vigilancia.puerto.RelojesVigilados;
import cl.ucn.alertacuida.configuracion.PropiedadesVigilancia;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reglas de ausencia contra la base real y con los valores de application.properties. Cada caso sale
 * de un requisito escrito (cronograma, notas del modelo o decisiones del 08-11-2026). La hora del
 * vigilante se fija para que el resultado no dependa de cuándo corren las pruebas. Sin Docker se omite.
 *
 * <p>Requisitos que se prueban:
 * <ul>
 * <li>R1 El sistema detecta por sí solo que un reloj dejó de sincronizar (4 horas), sin alertas
 * repetidas, con origen SERVIDOR y gravedad media.</li>
 * <li>R2 Cuando el reloj vuelve a la normalidad, el sistema cierra la alerta sin un profesional, y
 * después puede abrirse otra.</li>
 * <li>R3 Batería bajo 20 % y reloj no puesto de día, en 2 sincronizaciones seguidas, crean alertas
 * leves.</li>
 * <li>R4 Solo se vigilan los vínculos vigentes.</li>
 * <li>R5 Un simulacro no bloquea una alerta real, ni se cierra junto con ella.</li>
 * <li>R6 Si una persona cerró la alerta, no se crea otra hasta que el reloj se normalice y el problema
 * vuelva a aparecer.</li>
 * <li>R7 Las leves creadas fuera del horario hábil quedan pendientes para el día hábil; las medias
 * no.</li>
 * </ul>
 */
@SpringBootTest
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosReglasAusenciaTest {

	private static final Instant MEDIODIA = LocalDate.of(2026, 11, 9).atTime(12, 0).atZone(ZonaHoraria.CHILE)
			.toInstant();

	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private RelojesVigilados relojes;
	@Autowired
	private Alertas alertas;
	@Autowired
	private Transacciones transacciones;
	@Autowired
	private PropiedadesVigilancia propiedades;
	@Autowired
	private ReglaEnrutamiento enrutamiento;

	private UUID asignacion;

	/** Base vacía y un paciente con su reloj, vinculado desde el día anterior. */
	@BeforeEach
	void preparar() {
		for (String tabla : new String[] {"asignacion_reloj", "smartwatch", "paciente"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		UUID paciente = jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, 'hash-ausencia', CURRENT_DATE, CURRENT_DATE) RETURNING id_paciente""")
				.param("rut", cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT))
				.query(UUID.class).single();
		UUID reloj = jdbc.sql("""
				INSERT INTO smartwatch (identificador_reloj, estado) VALUES ('R-AUSENCIA', 'OPERATIVO')
				RETURNING id_dispositivo""")
				.query(UUID.class).single();
		asignacion = jdbc.sql("""
				INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
				VALUES (:reloj, :paciente, :inicio, 'hash-token-ausencia') RETURNING id_asignacion""")
				.param("reloj", reloj)
				.param("paciente", paciente)
				.param("inicio", Timestamp.from(MEDIODIA.minus(Duration.ofDays(1))))
				.query(UUID.class).single();
	}

	/** R1: un reloj que dejó de sincronizar genera una alerta del servidor, gravedad media. */
	@Test
	void unRelojCalladoGeneraSilencioDelServidor() {
		sincronizacion(MEDIODIA.minus(Duration.ofHours(4)), 80, true);

		vigilar(MEDIODIA);

		assertThat(jdbc.sql("""
				SELECT origen || '|' || severidad FROM alerta
				WHERE id_asignacion = :a AND tipo_evento = 'SILENCIO_DATOS'""")
				.param("a", asignacion).query(String.class).single()).isEqualTo("SERVIDOR|MEDIA");
	}

	/** R1: a las 3 horas y 59 minutos todavía no hay silencio. */
	@Test
	void antesDelPlazoNoHayAlerta() {
		sincronizacion(MEDIODIA.minus(Duration.ofMinutes(239)), 80, true);

		vigilar(MEDIODIA);

		assertThat(total("SILENCIO_DATOS")).isZero();
	}

	/** R1: un reloj que nunca sincronizó también se detecta. */
	@Test
	void relojQueNuncaSincronizoGeneraSilencio() {
		vigilar(MEDIODIA);

		assertThat(total("SILENCIO_DATOS")).isEqualTo(1);
	}

	/** R1: varias pasadas no repiten la alerta. */
	@Test
	void variasPasadasDejanUnaSolaAlerta() {
		vigilar(MEDIODIA);
		vigilar(MEDIODIA.plus(Duration.ofMinutes(5)));
		vigilar(MEDIODIA.plus(Duration.ofMinutes(10)));

		assertThat(total("SILENCIO_DATOS")).isEqualTo(1);
	}

	/** R2: vuelven los datos: queda RESUELTA y sin resolutor, y un silencio posterior abre otra. */
	@Test
	void alVolverLosDatosSeCierraSolaYDespuesPuedeAbrirseOtra() {
		vigilar(MEDIODIA);
		sincronizacion(MEDIODIA.plus(Duration.ofMinutes(1)), 80, true);

		vigilar(MEDIODIA.plus(Duration.ofMinutes(5)));

		assertThat(estadoAtencion()).isEqualTo("RESUELTA");
		assertThat(jdbc.sql("""
				SELECT count(*) FROM atencion_alerta t JOIN alerta a USING (id_alerta)
				WHERE a.id_asignacion = :a AND t.id_usuario_resolutor IS NULL AND t.fecha_hora_cierre IS NOT NULL""")
				.param("a", asignacion).query(Integer.class).single()).isEqualTo(1);

		vigilar(MEDIODIA.plus(Duration.ofHours(5)));

		assertThat(total("SILENCIO_DATOS")).isEqualTo(2);
	}

	/** R2: si alguien ya la había tomado, el cierre automático conserva quién la tomó. */
	@Test
	void elCierreConservaQuienLaTomo() {
		vigilar(MEDIODIA);
		UUID usuario = usuario();
		jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, id_usuario_responsable, fecha_hora_toma)
				SELECT id_alerta, :u, :toma FROM alerta WHERE id_asignacion = :a""")
				.param("u", usuario).param("toma", Timestamp.from(MEDIODIA.plus(Duration.ofMinutes(1))))
				.param("a", asignacion).update();
		sincronizacion(MEDIODIA.plus(Duration.ofMinutes(2)), 80, true);

		vigilar(MEDIODIA.plus(Duration.ofMinutes(5)));

		assertThat(estadoAtencion()).isEqualTo("RESUELTA");
		assertThat(jdbc.sql("""
				SELECT id_usuario_responsable FROM atencion_alerta t JOIN alerta a USING (id_alerta)
				WHERE a.id_asignacion = :a""").param("a", asignacion).query(UUID.class).single()).isEqualTo(usuario);
	}

	/** R3: dos sincronizaciones bajo 20 % crean BATERIA_BAJA leve, y recuperarla la cierra. */
	@Test
	void bateriaBajaSeAlertaYSeCierraSola() {
		sincronizacion(MEDIODIA.minus(Duration.ofHours(3)), 19, true);
		sincronizacion(MEDIODIA.minus(Duration.ofHours(1)), 19, true);

		vigilar(MEDIODIA);

		assertThat(jdbc.sql("""
				SELECT severidad FROM alerta WHERE id_asignacion = :a AND tipo_evento = 'BATERIA_BAJA'""")
				.param("a", asignacion).query(String.class).single()).isEqualTo("LEVE");

		sincronizacion(MEDIODIA.plus(Duration.ofMinutes(1)), 20, true);
		vigilar(MEDIODIA.plus(Duration.ofMinutes(5)));

		assertThat(estadoAtencion()).isEqualTo("RESUELTA");
	}

	/** R3: dos sincronizaciones de día sin poner crean RELOJ_NO_PUESTO leve. */
	@Test
	void relojNoPuestoDeDiaSeAlerta() {
		sincronizacion(MEDIODIA.minus(Duration.ofHours(3)), 80, false);
		sincronizacion(MEDIODIA.minus(Duration.ofHours(1)), 80, false);

		vigilar(MEDIODIA);

		assertThat(jdbc.sql("""
				SELECT severidad FROM alerta WHERE id_asignacion = :a AND tipo_evento = 'RELOJ_NO_PUESTO'""")
				.param("a", asignacion).query(String.class).single()).isEqualTo("LEVE");
	}

	/** R3: solo cuentan las dos últimas: una sincronización normal entre medio corta la racha. */
	@Test
	void soloCuentanLasDosUltimasSincronizaciones() {
		sincronizacion(MEDIODIA.minus(Duration.ofHours(3)), 19, true);
		sincronizacion(MEDIODIA.minus(Duration.ofHours(2)), 50, true);
		sincronizacion(MEDIODIA.minus(Duration.ofHours(1)), 19, true);

		vigilar(MEDIODIA);

		assertThat(total("BATERIA_BAJA")).isZero();
	}

	/** R4: un vínculo cerrado ya no se vigila. */
	@Test
	void unVinculoCerradoNoSeVigila() {
		jdbc.sql("UPDATE asignacion_reloj SET fecha_fin = :fin WHERE id_asignacion = :a")
				.param("fin", Timestamp.from(MEDIODIA.minus(Duration.ofHours(1)))).param("a", asignacion).update();

		vigilar(MEDIODIA);

		assertThat(total("SILENCIO_DATOS")).isZero();
	}

	/** R5: un simulacro de silencio abierto no impide crear la alerta real. */
	@Test
	void unSimulacroNoBloqueaLaAlertaReal() {
		simulacroDeSilencio();

		vigilar(MEDIODIA);

		assertThat(total("SILENCIO_DATOS")).isEqualTo(2);
	}

	/** R5: al volver los datos se cierra la alerta real, y el simulacro sigue su curso. */
	@Test
	void elCierreAutomaticoNoTocaLosSimulacros() {
		simulacroDeSilencio();
		sincronizacion(MEDIODIA.minus(Duration.ofMinutes(1)), 80, true);

		vigilar(MEDIODIA);

		assertThat(jdbc.sql("SELECT count(*) FROM atencion_alerta").query(Integer.class).single()).isZero();
	}

	/** R6: cerrada a mano, y sin datos nuevos del reloj, no se crea otra. */
	@Test
	void cerradaAManoNoSeRepiteMientrasSigaElSilencio() {
		vigilar(MEDIODIA);
		cerrarAMano(MEDIODIA.plus(Duration.ofMinutes(1)));

		vigilar(MEDIODIA.plus(Duration.ofMinutes(5)));
		vigilar(MEDIODIA.plus(Duration.ofHours(6)));

		assertThat(total("SILENCIO_DATOS")).isEqualTo(1);
	}

	/** R6: el reloj volvió a sincronizar y después calló de nuevo: es un problema nuevo. */
	@Test
	void trasVolverLosDatosUnSilencioNuevoSeAlerta() {
		vigilar(MEDIODIA);
		cerrarAMano(MEDIODIA.plus(Duration.ofMinutes(1)));
		sincronizacion(MEDIODIA.plus(Duration.ofMinutes(10)), 80, true);

		vigilar(MEDIODIA.plus(Duration.ofMinutes(15)));
		assertThat(total("SILENCIO_DATOS")).isEqualTo(1);

		vigilar(MEDIODIA.plus(Duration.ofHours(5)));
		assertThat(total("SILENCIO_DATOS")).isEqualTo(2);
	}

	/** R6: la batería sigue baja tras el cierre: no se repite; se recupera y vuelve a bajar: sí. */
	@Test
	void bateriaCerradaAManoSoloSeRepiteTrasRecuperarse() {
		sincronizacion(MEDIODIA.minus(Duration.ofHours(3)), 19, true);
		sincronizacion(MEDIODIA.minus(Duration.ofHours(1)), 19, true);
		vigilar(MEDIODIA);
		cerrarAMano(MEDIODIA.plus(Duration.ofMinutes(1)));
		sincronizacion(MEDIODIA.plus(Duration.ofHours(1)), 18, true);

		vigilar(MEDIODIA.plus(Duration.ofMinutes(65)));
		assertThat(total("BATERIA_BAJA")).isEqualTo(1);

		sincronizacion(MEDIODIA.plus(Duration.ofHours(2)), 90, true);
		sincronizacion(MEDIODIA.plus(Duration.ofHours(3)), 19, true);
		sincronizacion(MEDIODIA.plus(Duration.ofHours(4)), 19, true);
		vigilar(MEDIODIA.plus(Duration.ofMinutes(245)));

		assertThat(total("BATERIA_BAJA")).isEqualTo(2);
	}

	/** R7: batería baja de madrugada: queda en la bandeja del día hábil. */
	@Test
	void unaLeveDeMadrugadaQuedaParaElDiaHabil() {
		Instant madrugada = LocalDate.of(2026, 11, 9).atTime(3, 0).atZone(ZonaHoraria.CHILE).toInstant();
		sincronizacion(madrugada.minus(Duration.ofHours(3)), 19, true);
		sincronizacion(madrugada.minus(Duration.ofHours(1)), 19, true);

		vigilar(madrugada);

		assertThat(estadoContacto()).isEqualTo("PENDIENTE_DIA_HABIL");
	}

	/** R7: un silencio de madrugada es media y no queda pendiente. */
	@Test
	void unSilencioDeMadrugadaNoQuedaPendiente() {
		Instant madrugada = LocalDate.of(2026, 11, 9).atTime(3, 0).atZone(ZonaHoraria.CHILE).toInstant();

		vigilar(madrugada);

		assertThat(estadoContacto()).isNotEqualTo("PENDIENTE_DIA_HABIL");
	}

	/** Guarda una sincronización con esos datos. */
	private void sincronizacion(Instant recepcion, int bateria, boolean puesto) {
		jdbc.sql("""
				INSERT INTO sincronizacion (event_id, id_asignacion, fecha_hora_recepcion, nivel_bateria, reloj_puesto)
				VALUES (gen_random_uuid(), :a, :r, :b, :p)""")
				.param("a", asignacion).param("r", Timestamp.from(recepcion)).param("b", bateria).param("p", puesto)
				.update();
	}

	/** Deja abierto un simulacro con tipo silencio de datos. */
	private void simulacroDeSilencio() {
		jdbc.sql("""
				INSERT INTO alerta (event_id, id_asignacion, origen, tipo_evento, severidad, es_simulacro,
					fecha_hora_recepcion)
				VALUES (gen_random_uuid(), :a, 'SERVIDOR', 'SILENCIO_DATOS', 'LEVE', true, :r)""")
				.param("a", asignacion).param("r", Timestamp.from(MEDIODIA.minus(Duration.ofHours(1)))).update();
	}

	/** @return una cuenta nueva del equipo */
	private UUID usuario() {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo)
				VALUES ((SELECT min(codigo) FROM rol), 'x', :hash, 'Persona de prueba', :correo)
				RETURNING id_usuario""")
				.param("hash", "hash-usuario-" + UUID.randomUUID())
				.param("correo", UUID.randomUUID() + "@prueba.cl")
				.query(UUID.class).single();
	}

	/** Una persona del equipo cierra las alertas abiertas del vínculo. */
	private void cerrarAMano(Instant cierre) {
		jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, fecha_hora_cierre, id_usuario_resolutor)
				SELECT a.id_alerta, :cierre, :u FROM alerta a LEFT JOIN atencion_alerta t USING (id_alerta)
				WHERE a.id_asignacion = :a AND t.fecha_hora_cierre IS NULL""")
				.param("cierre", Timestamp.from(cierre)).param("u", usuario()).param("a", asignacion).update();
	}

	/** @return el estado de contacto de la primera alerta del vínculo */
	private String estadoContacto() {
		return jdbc.sql("""
				SELECT e.estado_contacto FROM alerta a JOIN alerta_estado e USING (id_alerta)
				WHERE a.id_asignacion = :a ORDER BY a.numero LIMIT 1""")
				.param("a", asignacion).query(String.class).single();
	}

	/** @return cuántas alertas de ese tipo tiene el vínculo */
	private int total(String tipo) {
		return jdbc.sql("SELECT count(*) FROM alerta WHERE id_asignacion = :a AND tipo_evento = :t")
				.param("a", asignacion).param("t", tipo).query(Integer.class).single();
	}

	/** @return el estado de atención de la primera alerta del vínculo */
	private String estadoAtencion() {
		return jdbc.sql("""
				SELECT e.estado_atencion FROM alerta a JOIN alerta_estado e USING (id_alerta)
				WHERE a.id_asignacion = :a ORDER BY a.numero LIMIT 1""")
				.param("a", asignacion).query(String.class).single();
	}

	/** Corre una pasada del vigilante a esa hora, con las reglas de application.properties. */
	private void vigilar(Instant ahora) {
		new VigilarRelojes(relojes, alertas, ReglasAusencia.todas(propiedades.parametros()), enrutamiento,
				transacciones, Clock.fixed(ahora, ZoneOffset.UTC)).vigilar();
	}

}
