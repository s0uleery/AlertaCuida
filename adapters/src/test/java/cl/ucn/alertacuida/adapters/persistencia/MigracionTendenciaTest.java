package cl.ucn.alertacuida.adapters.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migraciones V8 (tendencia), V9 (botón de ayuda) y V20 (orden por gravedad) sobre una base con datos, que
 * {@link ModeloDatosTest} no cubre porque parte vacía. Usa un PostgreSQL real (Docker). Sin Docker se omite.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigracionTendenciaTest {

	private static final String APAGADA = "00000000-0000-0000-0000-00000000000a";
	private static final String ACTIVA = "00000000-0000-0000-0000-00000000000b";
	private static final String SIN_CONFIGURACION = "00000000-0000-0000-0000-00000000000c";
	private static final String NUEVA = "00000000-0000-0000-0000-00000000000d";

	/** Misma versión que el servidor (ver Versiones.md). */
	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

	/** Activa el módulo con una versión nueva y auditada, sin tocar el historial ni otras opciones. */
	@Test
	void activaLaTendenciaAgregandoUnaVersionNueva() throws Exception {
		try (Connection conexion = conectar(); Statement sql = conexion.createStatement()) {
			sql.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
		}
		migrarHasta("7");
		try (Connection conexion = conectar(); Statement sql = conexion.createStatement()) {
			for (String id : new String[] {APAGADA, ACTIVA, SIN_CONFIGURACION, NUEVA}) {
				sql.execute("INSERT INTO paciente (id_paciente, rut, rut_hash, fecha_ingreso) "
						+ "VALUES ('" + id + "', 'cifrado', '" + id + "', '2026-01-10')");
			}
			sql.execute("""
					INSERT INTO configuracion (id_paciente, vigente_desde, tendencia_activa, gps_activo, minutos_espera,
						motivo_cambio) VALUES
					('%1$s', '2026-10-01 12:00Z', false, false, null, 'motivo-antiguo'),
					('%1$s', '2026-10-10 12:00Z', false, true, 8, 'motivo-reciente'),
					('%2$s', '2026-10-01 12:00Z', true, false, null, null)""".formatted(APAGADA, ACTIVA));

			migrarHasta("8");

			assertThat(versiones(sql, APAGADA)).isEqualTo("3");
			assertThat(texto(sql, """
					SELECT concat_ws(',', tendencia_activa::text, gps_activo::text, minutos_espera,
						coalesce(motivo_cambio, 'sin-motivo'))
					FROM configuracion WHERE id_paciente = '%s'
					ORDER BY vigente_desde DESC LIMIT 1""".formatted(APAGADA)))
					.isEqualTo("true,true,8,sin-motivo");
			assertThat(texto(sql, """
					SELECT count(*) FROM configuracion WHERE id_paciente = '%s' AND NOT tendencia_activa
					AND motivo_cambio IN ('motivo-antiguo', 'motivo-reciente')""".formatted(APAGADA))).isEqualTo("2");
			assertThat(versiones(sql, ACTIVA)).isEqualTo("1");
			assertThat(versiones(sql, SIN_CONFIGURACION)).isEqualTo("0");
			assertThat(texto(sql, """
					SELECT count(*) FROM registro_auditoria a JOIN configuracion c
						ON a.id_entidad = c.id_paciente || '|'
							|| to_char(c.vigente_desde AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
					WHERE a.accion = 'MODIFICAR' AND a.entidad = 'CONFIGURACION' AND a.id_usuario IS NULL
						AND c.id_paciente = '%s' AND c.tendencia_activa""".formatted(APAGADA))).isEqualTo("1");
			assertThat(texto(sql, "SELECT count(*) FROM registro_auditoria")).isEqualTo("1");

			sql.execute("INSERT INTO configuracion (id_paciente, vigente_desde) VALUES ('" + NUEVA + "', now())");
			assertThat(texto(sql, "SELECT tendencia_activa FROM configuracion WHERE id_paciente = '" + NUEVA + "'"))
					.isEqualTo("t");
		}
	}

	/**
	 * V9 activa el botón de ayuda con una versión nueva y auditada para cada paciente configurado: las filas
	 * anteriores quedan con el botón apagado (entonces no existía) y la zona segura apagada.
	 */
	@Test
	void activaElBotonDeAyudaAgregandoUnaVersionNueva() throws Exception {
		try (Connection conexion = conectar(); Statement sql = conexion.createStatement()) {
			sql.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
		}
		migrarHasta("8");
		try (Connection conexion = conectar(); Statement sql = conexion.createStatement()) {
			for (String id : new String[] {APAGADA, SIN_CONFIGURACION, NUEVA}) {
				sql.execute("INSERT INTO paciente (id_paciente, rut, rut_hash, fecha_ingreso) "
						+ "VALUES ('" + id + "', 'cifrado', '" + id + "', '2026-01-10')");
			}
			sql.execute("""
					INSERT INTO configuracion (id_paciente, vigente_desde, gps_activo, minutos_espera)
					VALUES ('%s', '2026-10-10 12:00Z', true, 8)""".formatted(APAGADA));

			migrarHasta("9");

			assertThat(versiones(sql, APAGADA)).isEqualTo("2");
			assertThat(texto(sql, """
					SELECT concat_ws(',', boton_ayuda_activo::text, zona_segura_activa::text, gps_activo::text,
						minutos_espera)
					FROM configuracion WHERE id_paciente = '%s'
					ORDER BY vigente_desde DESC LIMIT 1""".formatted(APAGADA))).isEqualTo("true,false,true,8");
			assertThat(texto(sql, """
					SELECT boton_ayuda_activo FROM configuracion WHERE id_paciente = '%s'
					AND vigente_desde = '2026-10-10 12:00Z'""".formatted(APAGADA))).isEqualTo("f");
			assertThat(versiones(sql, SIN_CONFIGURACION)).isEqualTo("0");
			assertThat(texto(sql, """
					SELECT count(*) FROM registro_auditoria WHERE accion = 'MODIFICAR' AND entidad = 'CONFIGURACION'
					AND id_usuario IS NULL AND id_entidad LIKE '%s|%%'""".formatted(APAGADA))).isEqualTo("1");

			sql.execute("INSERT INTO configuracion (id_paciente, vigente_desde) VALUES ('" + NUEVA + "', now())");
			assertThat(texto(sql, "SELECT boton_ayuda_activo FROM configuracion WHERE id_paciente = '" + NUEVA + "'"))
					.isEqualTo("t");
		}
	}

	/**
	 * V20 deja cada contacto activo en las tres gravedades con el orden que tenía (los desactivados, en
	 * ninguna), y pasa Familia y Vecino a Tercero significativo conservando el parentesco.
	 */
	@Test
	void copiaElOrdenALasTresGravedadesYCambiaLosTipos() throws Exception {
		try (Connection conexion = conectar(); Statement sql = conexion.createStatement()) {
			sql.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
		}
		migrarHasta("19");
		try (Connection conexion = conectar(); Statement sql = conexion.createStatement()) {
			sql.execute("INSERT INTO paciente (id_paciente, rut, rut_hash, fecha_ingreso) "
					+ "VALUES ('" + APAGADA + "', 'cifrado', 'rut-v20', '2026-01-10')");
			sql.execute("""
					INSERT INTO contacto (id_contacto, nombre, correo, correo_hash) VALUES
					('00000000-0000-0000-0000-0000000000c1', 'Hija', 'h@x.cl', 'c1'),
					('00000000-0000-0000-0000-0000000000c2', 'Vecino', 'v@x.cl', 'c2'),
					('00000000-0000-0000-0000-0000000000c3', 'Otra', 'o@x.cl', 'c3')""");
			sql.execute("""
					INSERT INTO contacto_paciente (id_contacto_paciente, id_paciente, id_contacto, codigo_tipo_contacto,
						parentesco, orden_prioridad, activo) VALUES
					('00000000-0000-0000-0000-0000000000a1', '%1$s', '00000000-0000-0000-0000-0000000000c1', 'FAMILIA',
						'hija', 1, true),
					('00000000-0000-0000-0000-0000000000a2', '%1$s', '00000000-0000-0000-0000-0000000000c2', 'VECINO',
						null, 2, true),
					('00000000-0000-0000-0000-0000000000a3', '%1$s', '00000000-0000-0000-0000-0000000000c3', 'OTRO',
						null, 3, false)""".formatted(APAGADA));

			migrarHasta("20");

			assertThat(texto(sql, "SELECT count(*) FROM orden_contacto")).isEqualTo("6");
			assertThat(texto(sql, """
					SELECT string_agg(severidad || '=' || orden, ',' ORDER BY severidad) FROM orden_contacto
					WHERE id_contacto_paciente = '00000000-0000-0000-0000-0000000000a2'"""))
					.isEqualTo("GRAVE=2,LEVE=2,MEDIA=2");
			assertThat(texto(sql, """
					SELECT count(*) FROM orden_contacto
					WHERE id_contacto_paciente = '00000000-0000-0000-0000-0000000000a3'""")).isEqualTo("0");
			assertThat(texto(sql, """
					SELECT string_agg(codigo_tipo_contacto || '/' || coalesce(parentesco, '-'), ','
						ORDER BY id_contacto_paciente) FROM contacto_paciente"""))
					.isEqualTo("TERCERO_SIGNIFICATIVO/hija,TERCERO_SIGNIFICATIVO/-,OTRO/-");
			assertThat(texto(sql, "SELECT count(*) FROM tipo_contacto WHERE codigo IN ('FAMILIA', 'VECINO')"))
					.isEqualTo("0");
			assertThat(texto(sql, """
					SELECT count(*) FROM information_schema.columns
					WHERE table_name = 'contacto_paciente' AND column_name = 'orden_prioridad'""")).isEqualTo("0");
		}
	}

	/** Aplica las migraciones hasta esa versión. */
	private static void migrarHasta(String version) {
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.target(version)
				.load()
				.migrate();
	}

	/** @return una conexión nueva a la base de prueba */
	private static Connection conectar() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	/** @return cuántas versiones de configuración tiene el paciente, como texto */
	private static String versiones(Statement sql, String idPaciente) throws SQLException {
		return texto(sql, "SELECT count(*) FROM configuracion WHERE id_paciente = '" + idPaciente + "'");
	}

	/** @return la primera columna de la primera fila, como texto */
	private static String texto(Statement sql, String consulta) throws SQLException {
		try (ResultSet fila = sql.executeQuery(consulta)) {
			fila.next();
			return fila.getString(1);
		}
	}

}
