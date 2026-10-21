package cl.ucn.alertacuida.adapters.persistencia;

import static cl.ucn.alertacuida.adapters.persistencia.Casos.Motivo.CHECK;
import static cl.ucn.alertacuida.adapters.persistencia.Casos.Motivo.FORANEA;
import static cl.ucn.alertacuida.adapters.persistencia.Casos.Motivo.NULO;
import static cl.ucn.alertacuida.adapters.persistencia.Casos.Motivo.SUPERPOSICION;
import static cl.ucn.alertacuida.adapters.persistencia.Casos.Motivo.UNICO;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Pruebas del modelo de datos (tarea 1.1.6) sobre la base que crean las migraciones de Flyway,
 * en un PostgreSQL real (Docker). Los casos van en orden y comparten datos inventados:
 * pacientes María, Pedro y Rosa; profesionales Ana y Luis. Explicación del modelo: docs/modelo-datos.md.
 * Sin Docker se omiten (no fallan); en el CI corren siempre.
 */
@Testcontainers(disabledWithoutDocker = true)
class ModeloDatosTest {

	/** Misma versión que el servidor (ver Versiones.md). */
	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

	private static final String T1 = "'2026-10-01 12:00Z'";
	private static final String T2 = "'2026-10-10 12:00Z'";

	private static Connection conexion;
	private static Casos c;

	@BeforeAll
	static void crearBase() throws Exception {
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.load()
				.migrate();
		conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		c = new Casos(conexion, identificadores());
	}

	@AfterAll
	static void cerrar() throws Exception {
		conexion.close();
	}

	@TestFactory
	Stream<DynamicContainer> modeloDeDatos() {
		return Stream.of(estructura(), rolesYProfesionales(), inicioDeSesion(), pacientes(), contactos(),
				configuracionYUmbrales(), relojesYAsignaciones(), datosDelReloj(), configuracionPendiente(),
				flujoDeUnaAlerta(), reglasDeLaAtencion(), gravedadYHorario(), cancelacionYSimulacro(),
				alertasTecnicas(), idempotencia(), avisosAContactos(), datosCalculados(), auditoria(),
				borradoAlFinDelPiloto());
	}

	// ---------- Datos de prueba ----------

	private static Map<String, String> identificadores() {
		Map<String, String> ids = new HashMap<>();
		Object[][] lista = {
			{"ANA", 1}, {"LUIS", 2}, {"ADMIN", 3}, {"NUEVA", 4},
			{"MARIA", 11}, {"PEDRO", 12}, {"ROSA", 13}, {"PAC_NUEVO", 18}, {"PAC_SIN_CONSENTIMIENTO", 19},
			{"HIJA", 21}, {"SEG_CIUDADANA", 22}, {"VECINO", 23}, {"CUIDADORA", 24},
			{"M1", 31}, {"M2", 32}, {"P1", 33}, {"P2", 34}, {"P3", 35}, {"R1", 36}, {"R2", 37},
			{"W1", 41}, {"W2", 42}, {"W3", 43}, {"W4", 44}, {"W5", 45},
			{"A_MARIA", 51}, {"A_PEDRO", 52}, {"A_ROSA_VIEJA", 53}, {"A_ROSA", 54},
			{"S_MARIA", 61}, {"S_PEDRO", 62}, {"S_ROSA_VIEJA", 63}, {"S_ROSA", 64}, {"S_MARIA_2", 65},
			{"CAIDA_MARIA", 71}, {"LEVE_PEDRO", 72}, {"MEDIA_ROSA", 73}, {"GRAVE_ROSA", 74},
			{"CANCELADA", 75}, {"SILENCIO", 76}, {"SIMULACRO", 77}, {"SILENCIO_2", 78}, {"GRAVE_ROSA_2", 79},
			{"ALERTA_X1", 90}, {"ALERTA_X2", 91}, {"ALERTA_X3", 92}, {"ALERTA_X4", 96}, {"ALERTA_X5", 97},
			{"ALERTA_INEXISTENTE", 98}, {"INEXISTENTE", 99},
		};
		for (Object[] par : lista) {
			ids.put((String) par[0], String.format("00000000-0000-0000-0000-%012d", (Integer) par[1]));
		}
		return ids;
	}

	private static DynamicContainer grupo(String nombre, DynamicTest... casos) {
		return DynamicContainer.dynamicContainer(nombre, Stream.of(casos));
	}

	private static String alertaReloj(String alerta, String asig, String tipo, String sev, String cols, String vals) {
		return "INSERT INTO alerta (id_alerta, event_id, id_asignacion, hash_configuracion, origen, tipo_evento,"
				+ " severidad, fecha_hora_deteccion, fecha_hora_recepcion" + cols + ") VALUES ('{" + alerta
				+ "}', gen_random_uuid(), '{" + asig + "}', 'h1', 'RELOJ', '" + tipo + "', '" + sev
				+ "', '2026-10-15 03:00-03', now()" + vals + ")";
	}

	private static String alertaReloj(String alerta, String asig, String tipo, String sev) {
		return alertaReloj(alerta, asig, tipo, sev, "", "");
	}

	private static String alertaServidor(String alerta, String asig, String tipo, String cols, String vals) {
		return "INSERT INTO alerta (id_alerta, event_id, id_asignacion, origen, tipo_evento, severidad,"
				+ " fecha_hora_recepcion" + cols + ") VALUES ('{" + alerta + "}', gen_random_uuid(), '{" + asig
				+ "}', 'SERVIDOR', '" + tipo + "', 'LEVE', now()" + vals + ")";
	}

	private static String alertaServidor(String alerta, String asig, String tipo) {
		return alertaServidor(alerta, asig, tipo, "", "");
	}

	private static String aviso(String alerta, String vinculo, int ronda, String cols, String vals, String canal) {
		return "INSERT INTO registro_intento (id_intento, event_id, id_alerta, id_contacto_paciente, canal, ronda,"
				+ " fecha_hora_envio" + cols + ") VALUES (gen_random_uuid(), gen_random_uuid(), '{" + alerta + "}', '{"
				+ vinculo + "}', '" + canal + "', " + ronda + ", now()" + vals + ")";
	}

	private static String aviso(String alerta, String vinculo, int ronda, String cols, String vals) {
		return aviso(alerta, vinculo, ronda, cols, vals, "WHATSAPP");
	}

	private static String aviso(String alerta, String vinculo, int ronda) {
		return aviso(alerta, vinculo, ronda, "", "", "WHATSAPP");
	}

	/** La receta de la nota de atencion_alerta para asumir un caso sin carreras. */
	private static String asumir(String alerta, String usuario, String hora) {
		return "INSERT INTO atencion_alerta (id_alerta, id_usuario_responsable, fecha_hora_toma) VALUES ('{" + alerta
				+ "}', '{" + usuario + "}', '" + hora + "') ON CONFLICT (id_alerta) DO UPDATE SET"
				+ " id_usuario_responsable = EXCLUDED.id_usuario_responsable,"
				+ " fecha_hora_toma = EXCLUDED.fecha_hora_toma"
				+ " WHERE atencion_alerta.fecha_hora_toma IS NULL AND atencion_alerta.fecha_hora_cierre IS NULL";
	}

	private static String atencion(String alerta, String cols, String vals) {
		return "INSERT INTO atencion_alerta (id_alerta" + cols + ") VALUES ('{" + alerta + "}'" + vals + ")";
	}

	private static DynamicTest estado(String caso, String alerta, String contacto,
			String atencion) {
		return c.vale(caso, "SELECT estado_contacto || ' / ' || estado_atencion n FROM alerta_estado"
				+ " WHERE id_alerta = '{" + alerta + "}'", contacto + " / " + atencion);
	}

	private static DynamicTest pendiente(String caso, String asig, String hash, boolean esperado) {
		return c.vale(caso, "SELECT (SELECT hash_configuracion FROM sincronizacion WHERE id_asignacion = '{" + asig
				+ "}' ORDER BY fecha_hora_recepcion DESC LIMIT 1) IS DISTINCT FROM '" + hash + "' n", esperado);
	}

	private static String usuario(String id, String rol, String nombre, String correo, String rutHash, String cols,
			String vals) {
		String valorId = id == null ? "gen_random_uuid()" : "'{" + id + "}'";
		return "INSERT INTO usuario (id_usuario, codigo_rol, rut, rut_hash, nombre_profesional, correo" + cols
				+ ") VALUES (" + valorId + ", '" + rol + "', 'rut-cifrado', '" + rutHash + "', '" + nombre + "', '"
				+ correo + "'" + vals + ")";
	}

	private static String usuario(String id, String rol, String nombre, String correo, String rutHash) {
		return usuario(id, rol, nombre, correo, rutHash, "", "");
	}

	private static String vinculo(String pac, String cont, String tipo, int prio, String cols, String vals) {
		return "INSERT INTO contacto_paciente (id_contacto_paciente, id_paciente, id_contacto, codigo_tipo_contacto,"
				+ " orden_prioridad" + cols + ") VALUES (gen_random_uuid(), '{" + pac + "}', '{" + cont + "}', '"
				+ tipo + "', " + prio + vals + ")";
	}

	private static String vinculo(String pac, String cont, String tipo, int prio) {
		return vinculo(pac, cont, tipo, prio, "", "");
	}

	private static String asignacion(String reloj, String pac, String ini, String fin, String token) {
		return "INSERT INTO asignacion_reloj (id_asignacion, id_dispositivo, id_paciente, fecha_inicio, fecha_fin,"
				+ " hash_token_reloj) VALUES (gen_random_uuid(), '{" + reloj + "}', '{" + pac + "}', '" + ini + "', "
				+ (fin == null ? "null" : "'" + fin + "'") + ", '" + token + "')";
	}

	private static String asignacion(String reloj, String pac, String ini) {
		return asignacion(reloj, pac, ini, null, "tok-x");
	}

	private static String lectura(String lote, String asig, String tipo, int valor, String hora) {
		return "INSERT INTO registro_vital (id_registro, id_sincronizacion, id_asignacion, tipo, valor,"
				+ " fecha_hora_medicion) VALUES (gen_random_uuid(), '{" + lote + "}', '{" + asig + "}', '" + tipo
				+ "', " + valor + ", '" + hora + "')";
	}

	private static String abiertaServidor(String asig, String tipo) {
		return "SELECT count(*) n FROM alerta a LEFT JOIN atencion_alerta t USING (id_alerta) WHERE a.id_asignacion"
				+ " = '{" + asig + "}' AND a.tipo_evento = '" + tipo + "' AND a.origen = 'SERVIDOR'"
				+ " AND NOT a.es_simulacro AND t.fecha_hora_cierre IS NULL";
	}

	private static String disponible(String vinculo, String hora) {
		String h = "'" + hora + "'::time";
		return "SELECT (hora_inicio IS NULL OR (hora_inicio < hora_fin AND " + h + " >= hora_inicio AND " + h
				+ " < hora_fin) OR (hora_inicio > hora_fin AND (" + h + " >= hora_inicio OR " + h + " < hora_fin)))"
				+ " n FROM contacto_paciente WHERE id_contacto_paciente = '{" + vinculo + "}'";
	}

	private static final String PACIENTE_NUEVO = """
			INSERT INTO paciente (id_paciente, rut, rut_hash, fecha_consentimiento, fecha_ingreso)
			VALUES ('{PAC_NUEVO}', 'c', 'rut-18', '2026-10-01', '2026-10-01')""";

	// ---------- Grupos de casos ----------

	private DynamicContainer estructura() {
		return grupo("1. Estructura de la base (migraciones)",
			c.vale("Migraciones aplicadas por Flyway", """
					SELECT count(*) n FROM flyway_schema_history WHERE success AND version IS NOT NULL""", 6),
			c.vale("Cantidad de tablas", """
					SELECT count(*) n FROM information_schema.tables WHERE table_schema = 'public'
					AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'""", 28),
			c.vale("Cantidad de relaciones (claves foráneas)", """
					SELECT count(*) n FROM information_schema.table_constraints
					WHERE table_schema = 'public' AND constraint_type = 'FOREIGN KEY'""", 31),
			c.vale("Tablas sueltas (sin ninguna relación)", """
					SELECT count(*) n FROM information_schema.tables t
					WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
					AND t.table_name <> 'flyway_schema_history'
					AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints k
						WHERE k.constraint_type = 'FOREIGN KEY' AND k.table_name = t.table_name)
					AND NOT EXISTS (SELECT 1 FROM information_schema.constraint_column_usage u
						JOIN information_schema.table_constraints k ON k.constraint_name = u.constraint_name
						AND k.constraint_type = 'FOREIGN KEY' WHERE u.table_name = t.table_name)""", 0),
			c.vale("La clave foránea de la ficha está en ficha_paciente (apunta a paciente)", """
					SELECT count(*) n FROM information_schema.table_constraints
					WHERE constraint_type = 'FOREIGN KEY' AND table_name = 'ficha_paciente'""", 1),
			c.vale("La clave foránea de la atención está en atencion_alerta (apunta a alerta)", """
					SELECT count(*) n FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY'
					AND table_name = 'atencion_alerta' AND constraint_name LIKE '%id_alerta%'""", 1),
			c.vale("Vistas de datos calculados", """
					SELECT count(*) n FROM information_schema.views WHERE table_schema = 'public'""", 1),
			c.vale("Ninguna columna jsonb en la configuración clínica", """
					SELECT count(*) n FROM information_schema.columns
					WHERE table_name IN ('configuracion', 'umbral') AND data_type = 'jsonb'""", 0),
			c.vale("La fecha de nacimiento (cifrada) se guarda como texto", """
					SELECT data_type n FROM information_schema.columns
					WHERE table_name = 'paciente' AND column_name = 'fecha_nacimiento'""", "character varying"),
			c.vale("Extensión btree_gist instalada (asignaciones sin superposición)", """
					SELECT count(*) n FROM pg_extension WHERE extname = 'btree_gist'""", 1));
	}

	private DynamicContainer rolesYProfesionales() {
		return grupo("2. Roles, permisos y profesionales",
			c.vale("Roles iniciales de la migración", "SELECT count(*) n FROM rol", 3),
			c.vale("El ADMINISTRADOR es de sistema y tiene todos los permisos (4 módulos x 5 acciones)", """
					SELECT count(*) n FROM permiso_rol p JOIN rol r ON r.codigo = p.codigo_rol
					WHERE r.codigo = 'ADMINISTRADOR' AND r.es_sistema""", 20),
			c.vale("El COORDINADOR tiene alertas, usuarios y reportes completos (3 módulos x 5 acciones)", """
					SELECT count(*) n FROM permiso_rol WHERE codigo_rol = 'COORDINADOR'
					AND modulo <> 'ADMINISTRACION'""", 15),
			c.vale("El COORDINADOR no tiene permisos de administración", """
					SELECT count(*) n FROM permiso_rol WHERE codigo_rol = 'COORDINADOR'
					AND modulo = 'ADMINISTRACION'""", 0),
			c.vale("El PERSONAL solo ve y edita alertas", """
					SELECT string_agg(modulo || '/' || accion, ', ' ORDER BY accion) n FROM permiso_rol
					WHERE codigo_rol = 'PERSONAL'""", "ALERTAS/EDITAR, ALERTAS/VER"),
			c.acepta("Alta de tres profesionales activos", String.join(";",
					usuario("ANA", "PERSONAL", "Ana", "ana@muni.cl", "rut-ana",
							", hash_contrasena, activo", ", 'h', true"),
					usuario("LUIS", "COORDINADOR", "Luis", "luis@muni.cl", "rut-luis",
							", hash_contrasena, activo", ", 'h', true"),
					usuario("ADMIN", "ADMINISTRADOR", "Admin", "admin@muni.cl", "rut-admin",
							", hash_contrasena, activo", ", 'h', true"))),
			c.vale("Ana puede editar alertas (permiso de su rol)", """
					SELECT count(*) n FROM usuario u JOIN permiso_rol p ON p.codigo_rol = u.codigo_rol
					WHERE u.id_usuario = '{ANA}' AND p.modulo = 'ALERTAS' AND p.accion = 'EDITAR'""", 1),
			c.vale("Ana no puede ver reportes", """
					SELECT count(*) n FROM usuario u JOIN permiso_rol p ON p.codigo_rol = u.codigo_rol
					WHERE u.id_usuario = '{ANA}' AND p.modulo = 'REPORTES' AND p.accion = 'VER'""", 0),
			c.acepta("El administrador le da a PERSONAL el permiso de ver reportes (se deshace)",
					"BEGIN; INSERT INTO permiso_rol VALUES ('PERSONAL', 'REPORTES', 'VER'); ROLLBACK"),
			c.rechaza("Permiso repetido", "INSERT INTO permiso_rol VALUES ('PERSONAL', 'ALERTAS', 'VER')",
					UNICO),
			c.rechaza("Módulo que no existe (FINANZAS)",
					"INSERT INTO permiso_rol VALUES ('PERSONAL', 'FINANZAS', 'VER')", CHECK),
			c.rechaza("Permiso de un rol que no existe",
					"INSERT INTO permiso_rol VALUES ('SUPERUSUARIO', 'ALERTAS', 'VER')", FORANEA),
			c.rechaza("Profesional con un rol que no existe",
					usuario(null, "SUPERUSUARIO", "X", "x@muni.cl", "rut-x"), FORANEA),
			c.rechaza("Borrar un rol que tiene profesionales", "DELETE FROM rol WHERE codigo = 'COORDINADOR'",
					FORANEA),
			c.acepta("Borrar un rol sin profesionales borra sus permisos (se deshace)", """
					BEGIN; INSERT INTO rol (codigo, nombre) VALUES ('TEMPORAL', 'Temporal');
					INSERT INTO permiso_rol VALUES ('TEMPORAL', 'ALERTAS', 'VER');
					DELETE FROM rol WHERE codigo = 'TEMPORAL'; ROLLBACK"""),
			c.backend("Quitar todos los permisos del ADMINISTRADOR (rol de sistema)",
					"DELETE FROM permiso_rol WHERE codigo_rol = 'ADMINISTRADOR'"),
			c.acepta("Cuenta nueva creada por el administrador: inactiva, sin contraseña, con enlace de activación",
					usuario("NUEVA", "PERSONAL", "Nueva", "nueva@muni.cl", "rut-nueva") + """
					; INSERT INTO token_accion (id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion)
					VALUES (gen_random_uuid(), '{NUEVA}', 'ACTIVACION', 'act-1', now(), now() + interval '2 days')"""),
			c.rechaza("Activar la cuenta sin contraseña",
					"UPDATE usuario SET activo = true WHERE id_usuario = '{NUEVA}'", CHECK),
			c.acepta("La usuaria define su contraseña: se activa y el enlace queda usado", """
					UPDATE usuario SET hash_contrasena = 'h', activo = true WHERE id_usuario = '{NUEVA}';
					UPDATE token_accion SET fecha_uso = now() WHERE hash_token = 'act-1'"""),
			c.rechaza("Dos profesionales con el mismo correo",
					usuario(null, "PERSONAL", "Otra", "ana@muni.cl", "rut-otra"), UNICO),
			c.rechaza("El mismo correo con otras mayúsculas (ANA@muni.cl): se guarda en minúsculas",
					usuario(null, "PERSONAL", "Otra", "ANA@muni.cl", "rut-otra"), CHECK),
			c.backend("Desactivar a una usuaria con un enlace de activación pendiente", """
					UPDATE usuario SET activo = false WHERE id_usuario = '{LUIS}';
					INSERT INTO token_accion (id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion)
			VALUES (gen_random_uuid(), '{LUIS}', 'ACTIVACION', 'act-luis', now(), now() + interval '2 days')"""),
			c.rechaza("Dos profesionales con el mismo RUN",
					usuario(null, "PERSONAL", "Otra", "otra@muni.cl", "rut-ana"), UNICO),
			c.rechaza("Profesional sin RUN", """
					INSERT INTO usuario (id_usuario, codigo_rol, rut_hash, nombre_profesional, correo)
					VALUES (gen_random_uuid(), 'PERSONAL', 'r', 'X', 'x@muni.cl')""", NULO),
			c.rechaza("Enlace con un tipo que no existe", """
					INSERT INTO token_accion (id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion)
					VALUES (gen_random_uuid(), '{ANA}', 'INVITACION', 'x', now(), now() + interval '30 minutes')""",
					CHECK),
			c.rechaza("Enlace repetido", """
					INSERT INTO token_accion (id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion)
			VALUES (gen_random_uuid(), '{ANA}', 'RECUPERACION', 'act-1', now(), now() + interval '30 minutes')""",
					UNICO),
			c.rechaza("Token de sesión que vence antes de emitirse", """
					INSERT INTO token_sesion (id_token, id_usuario, hash_token, fecha_emision, fecha_expiracion)
					VALUES (gen_random_uuid(), '{ANA}', 'ses-x', now(), now() - interval '1 day')""", CHECK),
			c.rechaza("Enlace que vence al mismo tiempo que se emite", """
					INSERT INTO token_accion (id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion)
					VALUES (gen_random_uuid(), '{ANA}', 'RECUPERACION', 'rec-x', now(), now())""", CHECK),
			c.acepta("Sesión y códigos de respaldo de Ana", """
					INSERT INTO token_sesion (id_token, id_usuario, hash_token, fecha_emision, fecha_expiracion)
					VALUES (gen_random_uuid(), '{ANA}', 'ses-1', now(), now() + interval '7 days');
					INSERT INTO codigo_respaldo (id_codigo, id_usuario, hash_codigo)
					VALUES (gen_random_uuid(), '{ANA}', 'cod-1'), (gen_random_uuid(), '{ANA}', 'cod-2')"""),
			c.rechaza("Código de respaldo repetido para la misma profesional", """
					INSERT INTO codigo_respaldo (id_codigo, id_usuario, hash_codigo)
					VALUES (gen_random_uuid(), '{ANA}', 'cod-1')""", UNICO),
			c.rechaza("Token de sesión repetido", """
					INSERT INTO token_sesion (id_token, id_usuario, hash_token, fecha_emision, fecha_expiracion)
					VALUES (gen_random_uuid(), '{LUIS}', 'ses-1', now(), now() + interval '7 days')""", UNICO));
	}

	private DynamicContainer inicioDeSesion() {
		String bloqueada = """
				SELECT count(*) >= 3 n FROM registro_auditoria f
		WHERE f.id_usuario = '{ANA}' AND f.accion = 'LOGIN_FALLIDO' AND f.fecha_hora > now() - interval '15 minutes'
				AND NOT EXISTS (SELECT 1 FROM registro_auditoria l
					WHERE l.id_usuario = f.id_usuario AND l.accion = 'LOGIN' AND l.fecha_hora > f.fecha_hora)""";
		return grupo("3. Inicio de sesión calculado desde la auditoría",
			c.acepta("Tres intentos fallidos de Ana en 10 minutos", """
					INSERT INTO registro_auditoria (id_auditoria, id_usuario, accion, fecha_hora, ip) VALUES
					(gen_random_uuid(), '{ANA}', 'LOGIN', now() - interval '1 day', '10.0.0.1'),
					(gen_random_uuid(), '{ANA}', 'LOGIN_FALLIDO', now() - interval '10 minutes', '10.0.0.1'),
					(gen_random_uuid(), '{ANA}', 'LOGIN_FALLIDO', now() - interval '8 minutes', '10.0.0.1'),
					(gen_random_uuid(), '{ANA}', 'LOGIN_FALLIDO', now() - interval '5 minutes', '10.0.0.1')"""),
			c.vale("Ana está bloqueada (3 fallidos en 15 min, valores del yml)", bloqueada, true),
			c.acepta("El administrador la desbloquea y Ana entra", """
					INSERT INTO registro_auditoria (id_auditoria, id_usuario, accion, fecha_hora)
					VALUES (gen_random_uuid(), '{ANA}', 'LOGIN', now() - interval '1 minute')"""),
			c.vale("Ya no está bloqueada", bloqueada, false),
			c.vale("Último acceso de Ana (hace 1 minuto)", """
					SELECT max(fecha_hora) > now() - interval '2 minutes' n FROM registro_auditoria
					WHERE id_usuario = '{ANA}' AND accion = 'LOGIN'""", true),
			c.acepta("Intento fallido con un RUN que no existe (sin usuario)", """
					INSERT INTO registro_auditoria (id_auditoria, accion, fecha_hora)
					VALUES (gen_random_uuid(), 'LOGIN_FALLIDO', now())"""),
			c.rechaza("Auditoría con entidad pero sin su id", """
					INSERT INTO registro_auditoria (id_auditoria, id_usuario, accion, entidad, fecha_hora)
					VALUES (gen_random_uuid(), '{ANA}', 'LEER', 'PACIENTE', now())""", CHECK),
			c.rechaza("Acción de auditoría que no existe", """
					INSERT INTO registro_auditoria (id_auditoria, accion, fecha_hora)
					VALUES (gen_random_uuid(), 'IMPRIMIR', now())""", CHECK));
	}

	private DynamicContainer pacientes() {
		String documento = """
				INSERT INTO documento (id_documento, id_paciente, tipo, nombre_archivo, ruta, hash_sha256, fecha_subida,
				id_usuario) VALUES (gen_random_uuid(), '{PEDRO}', '%s', 'x.pdf', '%s', 'sha-2', now(), %s)""";
		return grupo("4. Pacientes y datos de salud",
			c.acepta("Alta de tres pacientes con consentimiento firmado", """
					INSERT INTO paciente (id_paciente, rut, rut_hash, nombres, alias, fecha_nacimiento, sexo,
					nivel_dependencia, fecha_consentimiento, fecha_ingreso) VALUES
			('{MARIA}', 'c', 'rut-m', 'María', 'María', 'c-1940', 'FEMENINO', 'SEVERA', '2026-09-30', '2026-10-01'),
					('{PEDRO}', 'c', 'rut-p', 'Pedro', 'Pedro', 'c-1945', 'MASCULINO', 'MODERADA', '2026-09-30',
					'2026-10-01'),
			('{ROSA}', 'c', 'rut-r', 'Rosa', 'Rosa', 'c-1938', 'FEMENINO', null, '2026-09-30', '2026-10-01')"""),
			c.vale("Pacientes por evaluar (dependencia vacía)",
					"SELECT count(*) n FROM paciente WHERE nivel_dependencia IS NULL", 1),
			c.acepta("Ficha de María (uno a uno), dos enfermedades y dos situaciones", """
			INSERT INTO ficha_paciente (id_paciente, registro_discapacidad, notas) VALUES ('{MARIA}', true, 'x');
					INSERT INTO condicion_paciente (id_condicion, id_paciente, tipo, descripcion) VALUES
					(gen_random_uuid(), '{MARIA}', 'ENFERMEDAD', 'EPOC'),
					(gen_random_uuid(), '{MARIA}', 'ENFERMEDAD', 'Hipertensión'),
					(gen_random_uuid(), '{MARIA}', 'SITUACION', 'Movilidad reducida'),
					(gen_random_uuid(), '{MARIA}', 'SITUACION', 'Discapacidad visual')"""),
			c.vale("Ficha de María: enfermedades / situaciones", """
			SELECT count(*) FILTER (WHERE tipo = 'ENFERMEDAD') || ' / ' || count(*) FILTER (WHERE tipo = 'SITUACION')
					n FROM condicion_paciente WHERE id_paciente = '{MARIA}'""", "2 / 2"),
			c.vale("Pedro y Rosa no tienen ficha todavía (la ficha es opcional)", """
					SELECT count(*) n FROM paciente p WHERE NOT EXISTS
					(SELECT 1 FROM ficha_paciente f WHERE f.id_paciente = p.id_paciente)""", 2),
			c.rechaza("Segunda ficha para el mismo paciente",
					"INSERT INTO ficha_paciente (id_paciente) VALUES ('{MARIA}')", UNICO),
			c.rechaza("Ficha de un paciente que no existe",
					"INSERT INTO ficha_paciente (id_paciente) VALUES (gen_random_uuid())", FORANEA),
			c.rechaza("Condición sin descripción", """
					INSERT INTO condicion_paciente (id_condicion, id_paciente, tipo)
					VALUES (gen_random_uuid(), '{MARIA}', 'ENFERMEDAD')""", NULO),
			c.rechaza("Condición sin indicar si es enfermedad o situación", """
					INSERT INTO condicion_paciente (id_condicion, id_paciente, descripcion)
					VALUES (gen_random_uuid(), '{MARIA}', 'Diabetes')""", NULO),
			c.rechaza("Tipo de condición que no existe (ALERGIA)", """
					INSERT INTO condicion_paciente (id_condicion, id_paciente, tipo, descripcion)
					VALUES (gen_random_uuid(), '{MARIA}', 'ALERGIA', 'Penicilina')""", CHECK),
			c.acepta("Ana sube el consentimiento de María", """
					INSERT INTO documento (id_documento, id_paciente, tipo, nombre_archivo, ruta, hash_sha256,
					fecha_subida, id_usuario) VALUES (gen_random_uuid(), '{MARIA}', 'CONSENTIMIENTO',
					'consentimiento.pdf', '/docs/m/1.enc', 'sha-1', now(), '{ANA}')"""),
			c.rechaza("Dos documentos en la misma ruta", documento.formatted("OTRO", "/docs/m/1.enc", "'{ANA}'"),
					UNICO),
			c.rechaza("Documento subido por un usuario que no existe",
					documento.formatted("OTRO", "/docs/p/1.enc", "gen_random_uuid()"), FORANEA),
			c.rechaza("Tipo de documento que no existe", documento.formatted("RECETA", "/docs/p/1.enc", "'{ANA}'"),
					CHECK),
			c.rechaza("Paciente con RUN repetido", """
					INSERT INTO paciente (id_paciente, rut, rut_hash, fecha_ingreso)
					VALUES (gen_random_uuid(), 'c', 'rut-m', '2026-10-01')""", UNICO),
			c.rechaza("Paciente sin fecha de ingreso",
					"INSERT INTO paciente (id_paciente, rut, rut_hash) VALUES (gen_random_uuid(), 'c', 'rut-z')", NULO),
			c.rechaza("Baja anterior al ingreso",
					"UPDATE paciente SET fecha_baja = '2026-01-01' WHERE id_paciente = '{MARIA}'", CHECK),
			c.rechaza("Sexo que no existe en el catálogo", """
					INSERT INTO paciente (id_paciente, rut, rut_hash, sexo, fecha_ingreso)
					VALUES (gen_random_uuid(), 'c', 'rut-z', 'OTRO', '2026-10-01')""", CHECK),
			c.rechaza("Paciente con rut_hash pero sin el RUN cifrado", """
					INSERT INTO paciente (id_paciente, rut_hash, fecha_ingreso)
					VALUES (gen_random_uuid(), 'rut-z', '2026-10-01')""", NULO));
	}

	private DynamicContainer contactos() {
		return grupo("5. Contactos (compartidos, exclusivos y horarios)",
			c.vale("Tipos de contacto iniciales de la migración", """
					SELECT count(*) n FROM tipo_contacto
					WHERE codigo IN ('FAMILIA', 'VECINO', 'SEGURIDAD_CIUDADANA', 'OTRO')""", 4),
			c.acepta("Cuatro personas de contacto (una solo con correo)", """
			INSERT INTO contacto (id_contacto, nombre, whatsapp, whatsapp_hash, fecha_opt_in, correo, correo_hash)
					VALUES ('{HIJA}', 'Hija de María', '+56911', 'w-hija', now(), null, null),
					('{SEG_CIUDADANA}', 'Seguridad Ciudadana', '+56922', 'w-sc', now(), 'sc@muni.cl', 'm-sc'),
					('{VECINO}', 'Vecino Juan', null, null, null, 'juan@mail.cl', 'm-juan'),
					('{CUIDADORA}', 'Cuidadora de Pedro', '+56944', 'w-cuid', now(), null, null)"""),
			c.acepta("Listas de contactos: Seguridad Ciudadana en los tres pacientes; la cuidadora de Pedro de noche",
					"""
					INSERT INTO contacto_paciente (id_contacto_paciente, id_paciente, id_contacto, codigo_tipo_contacto,
					parentesco, es_cuidador_principal, orden_prioridad, hora_inicio, hora_fin) VALUES
					('{M1}', '{MARIA}', '{HIJA}', 'FAMILIA', 'hija', true, 1, null, null),
					('{M2}', '{MARIA}', '{SEG_CIUDADANA}', 'SEGURIDAD_CIUDADANA', null, false, 2, null, null),
					('{P1}', '{PEDRO}', '{CUIDADORA}', 'OTRO', null, true, 1, '22:00', '07:00'),
					('{P2}', '{PEDRO}', '{SEG_CIUDADANA}', 'SEGURIDAD_CIUDADANA', null, false, 2, null, null),
					('{P3}', '{PEDRO}', '{VECINO}', 'VECINO', null, false, 3, null, null),
					('{R1}', '{ROSA}', '{SEG_CIUDADANA}', 'SEGURIDAD_CIUDADANA', null, false, 1, null, null),
					('{R2}', '{ROSA}', '{VECINO}', 'VECINO', null, false, 2, null, null)"""),
			c.vale("Seguridad Ciudadana está guardada una sola vez",
					"SELECT count(*) n FROM contacto WHERE whatsapp_hash = 'w-sc'", 1),
			c.vale("Seguridad Ciudadana es contacto de tres pacientes (compartido)",
					"SELECT count(*) n FROM contacto_paciente WHERE id_contacto = '{SEG_CIUDADANA}'", 3),
			c.vale("La cuidadora de Pedro está disponible a las 03:00 (horario que cruza la medianoche)",
					disponible("P1", "03:00"), true),
			c.vale("La cuidadora de Pedro no está disponible a las 15:00", disponible("P1", "15:00"), false),
			c.filas("Meta informa la baja del opt-in de un número: se encuentra a la persona por su hash",
					"UPDATE contacto SET fecha_revocacion_opt_in = now() WHERE whatsapp_hash = 'w-cuid'", 1),
			c.rechaza("Misma persona registrada dos veces (mismo WhatsApp)", """
					INSERT INTO contacto (id_contacto, nombre, whatsapp, whatsapp_hash)
					VALUES (gen_random_uuid(), 'Otra', '+56922', 'w-sc')""", UNICO),
			c.rechaza("Misma persona registrada dos veces (mismo correo)", """
					INSERT INTO contacto (id_contacto, nombre, correo, correo_hash)
					VALUES (gen_random_uuid(), 'Otro', 'juan@mail.cl', 'm-juan')""", UNICO),
			c.rechaza("Persona sin WhatsApp ni correo",
					"INSERT INTO contacto (id_contacto, nombre) VALUES (gen_random_uuid(), 'X')", CHECK),
			c.rechaza("WhatsApp sin su hash",
					"INSERT INTO contacto (id_contacto, nombre, whatsapp) VALUES (gen_random_uuid(), 'X', '+56999')",
					CHECK),
			c.rechaza("Correo sin su hash",
					"INSERT INTO contacto (id_contacto, nombre, correo) VALUES (gen_random_uuid(), 'X', 'x@x.cl')",
					CHECK),
			c.rechaza("Baja del opt-in sin opt-in previo", """
					INSERT INTO contacto (id_contacto, nombre, correo, correo_hash, fecha_revocacion_opt_in)
					VALUES (gen_random_uuid(), 'X', 'x@x.cl', 'm-x', now())""", CHECK),
			c.rechaza("La misma persona dos veces en la lista de un paciente",
					vinculo("MARIA", "HIJA", "FAMILIA", 5), UNICO),
			c.rechaza("Dos contactos activos con la misma prioridad", vinculo("MARIA", "VECINO", "VECINO", 1), UNICO),
			c.acepta("Repetir prioridad con un contacto inactivo (se deshace)",
					"BEGIN; " + vinculo("MARIA", "VECINO", "VECINO", 1, ", activo", ", false") + "; ROLLBACK"),
			c.rechaza("Dos cuidadores principales activos para el mismo paciente",
					vinculo("MARIA", "VECINO", "VECINO", 3, ", es_cuidador_principal", ", true"), UNICO),
			c.rechaza("Prioridad 0", vinculo("MARIA", "VECINO", "VECINO", 0), CHECK),
			c.rechaza("Horario con inicio pero sin fin",
					vinculo("MARIA", "VECINO", "VECINO", 3, ", hora_inicio", ", '08:00'"), CHECK),
			c.rechaza("Horario que empieza y termina a la misma hora",
					vinculo("MARIA", "VECINO", "VECINO", 3, ", hora_inicio, hora_fin", ", '08:00', '08:00'"), CHECK),
			c.rechaza("Parentesco en un contacto que no es FAMILIA",
					vinculo("MARIA", "VECINO", "VECINO", 3, ", parentesco", ", 'sobrino'"), CHECK),
			c.rechaza("Tipo de contacto que no existe (AMBULANCIA)", vinculo("MARIA", "VECINO", "AMBULANCIA", 3),
					FORANEA));
	}

	private DynamicContainer configuracionYUmbrales() {
		String umbral = "INSERT INTO umbral VALUES ('{PEDRO}', '%s', now(), %s)";
		return grupo("6. Configuración clínica y umbrales (historiales separados)",
			c.acepta("María: configuración inicial y un cambio posterior (activa el GPS)",
					"INSERT INTO configuracion (id_paciente, vigente_desde) VALUES ('{MARIA}', " + T1 + ");"
					+ " INSERT INTO configuracion"
					+ " (id_paciente, vigente_desde, gps_activo, minutos_espera, motivo_cambio)"
					+ " VALUES ('{MARIA}', " + T2 + ", true, 5, 'Sale sola a caminar')"),
			c.acepta("Pedro con valores por defecto; Rosa en modo conservador",
					"INSERT INTO configuracion (id_paciente, vigente_desde) VALUES ('{PEDRO}', " + T1 + ");"
					+ " INSERT INTO configuracion (id_paciente, vigente_desde, modo_conservador)"
					+ " VALUES ('{ROSA}', " + T1 + ", true)"),
			c.vale("Módulos por defecto (caídas, FC, SpO2, inactividad sí; tendencia y GPS no)", """
			SELECT concat_ws(',', caidas_activa::text, fc_activa::text, spo2_activa::text, inactividad_activa::text,
					tendencia_activa::text, gps_activo::text) n FROM configuracion WHERE id_paciente = '{PEDRO}'""",
					"true,true,true,true,false,false"),
			c.vale("Configuración vigente de María (la más reciente): GPS activo", """
					SELECT gps_activo n FROM configuracion WHERE id_paciente = '{MARIA}'
					ORDER BY vigente_desde DESC LIMIT 1""", true),
			c.acepta("Umbrales de María: FC (dos versiones) y SpO2 sin límite alto",
					"INSERT INTO umbral VALUES ('{MARIA}', 'FC', " + T1 + ", 40, 50, 110, 130),"
					+ " ('{MARIA}', 'FC', " + T2 + ", 45, 55, 110, 130),"
					+ " ('{MARIA}', 'SPO2', " + T1 + ", 85, 90, null, null)"),
			c.vale("Historiales independientes: 2 configuraciones y 3 umbrales de María", """
					SELECT (SELECT count(*) FROM configuracion WHERE id_paciente = '{MARIA}') || ' / '
					|| (SELECT count(*) FROM umbral WHERE id_paciente = '{MARIA}') n""", "2 / 3"),
			c.vale("Umbral FC vigente de María: normal desde 55", """
					SELECT normal_min n FROM umbral WHERE id_paciente = '{MARIA}' AND variable = 'FC'
					ORDER BY vigente_desde DESC LIMIT 1""", 55),
			c.vale("Umbral FC que regía el 05-10 (versión del 01-10): normal desde 50", """
					SELECT normal_min n FROM umbral WHERE id_paciente = '{MARIA}' AND variable = 'FC'
					AND vigente_desde <= '2026-10-05' ORDER BY vigente_desde DESC LIMIT 1""", 50),
			c.acepta("María vuelve al umbral base de SpO2 sin borrar historial (fila con las cuatro columnas vacías)",
					"INSERT INTO umbral (id_paciente, variable, vigente_desde) VALUES ('{MARIA}', 'SPO2', " + T2 + ")"),
			c.vale("Para SpO2 rige el umbral base", """
					SELECT (grave_bajo IS NULL AND normal_min IS NULL AND normal_max IS NULL AND grave_alto IS NULL) n
					FROM umbral WHERE id_paciente = '{MARIA}' AND variable = 'SPO2'
					ORDER BY vigente_desde DESC LIMIT 1""", true),
			c.vale("Pedro no tiene umbrales propios (rige el base)",
					"SELECT count(*) n FROM umbral WHERE id_paciente = '{PEDRO}'", 0),
			c.rechaza("Dos configuraciones con la misma fecha",
					"INSERT INTO configuracion (id_paciente, vigente_desde) VALUES ('{MARIA}', " + T2 + ")", UNICO),
			c.rechaza("Minutos de espera en cero", """
					INSERT INTO configuracion (id_paciente, vigente_desde, minutos_espera)
					VALUES ('{MARIA}', now(), 0)""", CHECK),
			c.rechaza("Dos umbrales de la misma variable con la misma fecha",
					"INSERT INTO umbral (id_paciente, variable, vigente_desde) VALUES ('{MARIA}', 'FC', " + T2 + ")",
					UNICO),
			c.rechaza("Normal mínimo mayor que el normal máximo", umbral.formatted("FC", "40, 110, 50, 130"), CHECK),
			c.rechaza("Grave bajo por encima del normal mínimo", umbral.formatted("FC", "60, 50, 110, 130"), CHECK),
			c.rechaza("Grave alto por debajo del normal máximo", umbral.formatted("FC", "40, 50, 110, 100"), CHECK),
			c.rechaza("Umbral de una variable que no existe (presión arterial)",
					"INSERT INTO umbral (id_paciente, variable, vigente_desde) VALUES ('{PEDRO}', 'PRESION', now())",
					CHECK),
			c.rechaza("Umbral de FC a medias (solo grave bajo y grave alto)",
					umbral.formatted("FC", "120, null, null, 40"), CHECK),
			c.rechaza("Umbral de FC sin límite alto", umbral.formatted("FC", "40, 50, null, null"), CHECK),
			c.rechaza("Umbral de SpO2 con límite alto", umbral.formatted("SPO2", "85, 90, 150, null"), CHECK),
			c.rechaza("Umbral de SpO2 con normal desde 101 %", umbral.formatted("SPO2", "95, 101, null, null"), CHECK),
			c.acepta("Umbral de FC completo y coherente para Pedro (se deshace)",
					"BEGIN; " + umbral.formatted("FC", "40, 50, 100, 120") + "; ROLLBACK"));
	}

	private DynamicContainer relojesYAsignaciones() {
		return grupo("7. Relojes y asignaciones",
			c.acepta("Registro de cinco relojes con su SIM (uno retirado)", """
			INSERT INTO smartwatch (id_dispositivo, identificador_reloj, iccid, numero_sim, fecha_vencimiento_plan,
					estado) VALUES
					('{W1}', 'W-001', 'icc-1', '+56961', '2027-01-01', 'OPERATIVO'),
					('{W2}', 'W-002', 'icc-2', '+56962', '2027-01-01', 'OPERATIVO'),
					('{W3}', 'W-003', 'icc-3', '+56963', '2027-01-01', 'OPERATIVO'),
					('{W4}', 'W-004', 'icc-4', '+56964', '2027-01-01', 'OPERATIVO'),
					('{W5}', 'W-005', 'icc-5', null, null, 'RETIRADO')"""),
			c.rechaza("Dos relojes con la misma SIM (ICCID)", """
					INSERT INTO smartwatch (id_dispositivo, identificador_reloj, iccid, estado)
					VALUES (gen_random_uuid(), 'W-009', 'icc-1', 'OPERATIVO')""", UNICO),
			c.rechaza("Dos relojes con el mismo identificador", """
					INSERT INTO smartwatch (id_dispositivo, identificador_reloj, estado)
					VALUES (gen_random_uuid(), 'W-001', 'OPERATIVO')""", UNICO),
			c.rechaza("Reloj sin estado", """
					INSERT INTO smartwatch (id_dispositivo, identificador_reloj) VALUES (gen_random_uuid(), 'W-009')""",
					NULO),
			c.vale("Relojes con el plan vencido al 20-10-2026",
					"SELECT count(*) n FROM smartwatch WHERE fecha_vencimiento_plan < '2026-10-20'", 0),
			c.acepta("Asignación de un reloj a cada paciente",
					"INSERT INTO asignacion_reloj (id_asignacion, id_dispositivo, id_paciente, fecha_inicio,"
					+ " hash_token_reloj) VALUES ('{A_MARIA}', '{W1}', '{MARIA}', " + T1 + ", 'tok-1'),"
					+ " ('{A_PEDRO}', '{W2}', '{PEDRO}', " + T1 + ", 'tok-2'),"
					+ " ('{A_ROSA_VIEJA}', '{W3}', '{ROSA}', " + T1 + ", 'tok-3')"),
			c.acepta("Lote del reloj antiguo de Rosa",
					"INSERT INTO sincronizacion (id_sincronizacion, event_id, id_asignacion, hash_configuracion,"
					+ " fecha_hora_recepcion, nivel_bateria) VALUES ('{S_ROSA_VIEJA}', gen_random_uuid(),"
					+ " '{A_ROSA_VIEJA}', 'h1', " + T2 + ", 40)"),
			c.acepta("Reemplazo del reloj de Rosa: se cierra la asignación y se abre otra", """
					UPDATE asignacion_reloj SET fecha_fin = '2026-10-12 12:00Z' WHERE id_asignacion = '{A_ROSA_VIEJA}';
			INSERT INTO asignacion_reloj (id_asignacion, id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
					VALUES ('{A_ROSA}', '{W4}', '{ROSA}', '2026-10-12 12:00Z', 'tok-4')"""),
			c.vale("Rosa conserva el historial: 2 asignaciones, 1 vigente", """
					SELECT count(*) || '/' || count(*) FILTER (WHERE fecha_fin IS NULL) n FROM asignacion_reloj
					WHERE id_paciente = '{ROSA}'""", "2/1"),
			c.vale("El token del reloj antiguo ya no vale",
					"SELECT count(*) n FROM asignacion_reloj WHERE hash_token_reloj = 'tok-3' AND fecha_fin IS NULL",
					0),
			c.vale("El lote antiguo sigue ligado a Rosa", """
			SELECT a.id_paciente = '{ROSA}' n FROM sincronizacion s JOIN asignacion_reloj a USING (id_asignacion)
					WHERE s.id_sincronizacion = '{S_ROSA_VIEJA}'""", true),
			c.vale("Relojes libres para asignar (operativos y sin asignación vigente)", """
					SELECT count(*) n FROM smartwatch w WHERE estado = 'OPERATIVO' AND NOT EXISTS
			(SELECT 1 FROM asignacion_reloj a WHERE a.id_dispositivo = w.id_dispositivo AND a.fecha_fin IS NULL)""",
					1),
			c.rechaza("Un reloj asignado a dos pacientes a la vez", asignacion("W1", "PEDRO", "2026-10-20"),
					SUPERPOSICION),
			c.rechaza("Un paciente con dos relojes vigentes", asignacion("W3", "MARIA", "2026-10-20"), SUPERPOSICION),
			c.rechaza("Reusar el token de una asignación cerrada",
					asignacion("W3", "ROSA", "2026-10-20", "2026-10-21", "tok-3"), UNICO),
			c.rechaza("Asignación sin token", """
					INSERT INTO asignacion_reloj (id_asignacion, id_dispositivo, id_paciente, fecha_inicio)
					VALUES (gen_random_uuid(), '{W3}', '{MARIA}', '2020-01-01')""", NULO),
			c.rechaza("Asignación que termina antes de empezar",
					asignacion("W3", "MARIA", "2026-05-02", "2026-05-01", "tok-x"), CHECK),
			c.rechaza("Una asignación cerrada del reloj antiguo de Rosa que se superpone con su historial",
					asignacion("W3", "PEDRO", "2026-10-05", "2026-10-06", "tok-x"), SUPERPOSICION),
			c.rechaza("Una asignación cerrada de Rosa que se superpone con sus relojes",
					asignacion("W5", "ROSA", "2026-10-11", "2026-10-13", "tok-x"), SUPERPOSICION),
			c.acepta("El reloj antiguo de Rosa se asigna a otro paciente desde que se cerró (se deshace)",
					"BEGIN; " + PACIENTE_NUEVO + "; " + asignacion("W3", "PAC_NUEVO", "2026-10-12 12:00Z")
					+ "; ROLLBACK"),
			c.backend("Asignar un reloj RETIRADO", PACIENTE_NUEVO + "; " + asignacion("W5", "PAC_NUEVO", "2026-10-20")),
			c.backend("Asignar un reloj a un paciente sin consentimiento firmado", """
					INSERT INTO paciente (id_paciente, rut, rut_hash, fecha_ingreso)
					VALUES ('{PAC_SIN_CONSENTIMIENTO}', 'c', 'rut-sc', '2026-10-01');"""
					+ asignacion("W3", "PAC_SIN_CONSENTIMIENTO", "2026-10-20")),
			c.backend("Dar de baja a un paciente que tiene un reloj vigente",
					"UPDATE paciente SET fecha_baja = '2026-10-20' WHERE id_paciente = '{PEDRO}'"));
	}

	private DynamicContainer datosDelReloj() {
		return grupo("8. Datos recibidos del reloj (lotes, lecturas, GPS)",
			c.acepta("Lotes de cada reloj (LTE y Wi-Fi)", """
					INSERT INTO sincronizacion (id_sincronizacion, event_id, id_asignacion, hash_configuracion,
					fecha_hora_envio, fecha_hora_recepcion, nivel_bateria, reloj_puesto, minutos_puesto, tipo_conexion,
					version_app) VALUES
			('{S_MARIA}', gen_random_uuid(), '{A_MARIA}', 'h1', '2026-10-15 13:00Z', '2026-10-15 13:01Z', 80, true,
					110, 'LTE', '1.0.0'),
			('{S_PEDRO}', gen_random_uuid(), '{A_PEDRO}', 'h1', '2026-10-15 13:00Z', '2026-10-15 13:01Z', 15, true,
					120, 'WIFI', '1.0.0'),
			('{S_ROSA}', gen_random_uuid(), '{A_ROSA}', 'h1', '2026-10-15 13:00Z', '2026-10-15 13:01Z', 95, false,
					30, 'LTE', '1.0.0')"""),
			c.acepta("Lecturas de FC y SpO2 con su hora de medición", String.join(";",
					lectura("S_MARIA", "A_MARIA", "FC", 70, "2026-10-15 12:00Z"),
					lectura("S_MARIA", "A_MARIA", "FC", 80, "2026-10-15 12:30Z"),
					lectura("S_MARIA", "A_MARIA", "FC", 90, "2026-10-15 13:00Z"),
					lectura("S_MARIA", "A_MARIA", "SPO2", 97, "2026-10-15 12:00Z"),
					lectura("S_PEDRO", "A_PEDRO", "SPO2", 85, "2026-10-15 12:00Z"),
					lectura("S_PEDRO", "A_PEDRO", "FC", 75, "2026-10-15 12:00Z"))),
			c.rechaza("Lectura con una asignación distinta a la de su lote (redundancia controlada)",
					lectura("S_MARIA", "A_PEDRO", "FC", 70, "2026-10-15 14:00Z"), FORANEA),
			c.acepta("Segundo lote de María, que reenvía historial", """
					INSERT INTO sincronizacion (id_sincronizacion, event_id, id_asignacion, hash_configuracion,
					fecha_hora_recepcion, nivel_bateria)
					VALUES ('{S_MARIA_2}', gen_random_uuid(), '{A_MARIA}', 'h1', '2026-10-15 15:01Z', 70)"""),
			c.rechaza("Lectura reenviada en otro lote", lectura("S_MARIA_2", "A_MARIA", "FC", 70, "2026-10-15 12:00Z"),
					UNICO),
			c.filas("El backend ignora la lectura reenviada (ON CONFLICT DO NOTHING)",
					lectura("S_MARIA_2", "A_MARIA", "FC", 70, "2026-10-15 12:00Z") + " ON CONFLICT DO NOTHING", 0),
			c.rechaza("Lectura de un tipo que no existe (presión arterial)",
					lectura("S_MARIA", "A_MARIA", "PRESION", 120, "2026-10-15 14:00Z"), CHECK),
			c.rechaza("Lectura de un lote que no existe",
					lectura("INEXISTENTE", "A_MARIA", "FC", 70, "2026-10-15 14:00Z"), FORANEA),
			c.acepta("Ubicación GPS de María", """
					INSERT INTO ubicacion (id_ubicacion, id_sincronizacion, id_asignacion, latitud, longitud,
					precision_metros, fecha_hora_medicion)
					VALUES (gen_random_uuid(), '{S_MARIA}', '{A_MARIA}', -33.45, -70.66, 12, '2026-10-15 12:00Z')"""),
			c.rechaza("Ubicación repetida para la misma hora", """
					INSERT INTO ubicacion (id_ubicacion, id_sincronizacion, id_asignacion, latitud, longitud,
					fecha_hora_medicion)
			VALUES (gen_random_uuid(), '{S_MARIA_2}', '{A_MARIA}', -33.45, -70.66, '2026-10-15 12:00Z')""", UNICO),
			c.rechaza("Ubicación con una asignación distinta a la de su lote", """
					INSERT INTO ubicacion (id_ubicacion, id_sincronizacion, id_asignacion, latitud, longitud,
					fecha_hora_medicion)
			VALUES (gen_random_uuid(), '{S_MARIA}', '{A_PEDRO}', -33.45, -70.66, '2026-10-15 14:00Z')""", FORANEA),
			c.acepta("Eventos descartados por el reloj, por tipo", """
					INSERT INTO evento_descartado VALUES ('{S_MARIA}', 'SPO2', 3), ('{S_MARIA}', 'CAIDA', 1),
					('{S_PEDRO}', 'SPO2', 2)"""),
			c.rechaza("Contador de descartes en cero", "INSERT INTO evento_descartado VALUES ('{S_ROSA}', 'FC', 0)",
					CHECK),
			c.rechaza("Contador de descartes repetido para el mismo tipo",
					"INSERT INTO evento_descartado VALUES ('{S_MARIA}', 'SPO2', 1)", UNICO),
			c.rechaza("El reloj informa descartes de un evento que solo crea el servidor (SILENCIO_DATOS)",
					"INSERT INTO evento_descartado VALUES ('{S_ROSA}', 'SILENCIO_DATOS', 1)", CHECK),
			c.rechaza("Lote repetido (mismo event_id, el reloj reintentó)", """
					INSERT INTO sincronizacion (id_sincronizacion, event_id, id_asignacion, fecha_hora_recepcion)
					SELECT gen_random_uuid(), event_id, id_asignacion, now() FROM sincronizacion
					WHERE id_sincronizacion = '{S_MARIA}'""", UNICO),
			c.rechaza("Batería de 150 %",
					"UPDATE sincronizacion SET nivel_bateria = 150 WHERE id_sincronizacion = '{S_MARIA}'", CHECK),
			c.rechaza("Minutos puesto negativos",
					"UPDATE sincronizacion SET minutos_puesto = -1 WHERE id_sincronizacion = '{S_MARIA}'", CHECK),
			c.vale("Estado actual del reloj de María (último lote): batería", """
					SELECT nivel_bateria n FROM sincronizacion WHERE id_asignacion = '{A_MARIA}'
					ORDER BY fecha_hora_recepcion DESC LIMIT 1""", 70));
	}

	private DynamicContainer configuracionPendiente() {
		return grupo("9. Configuración pendiente en el reloj (por hash)",
			pendiente("Pedro: el reloj tiene h1 y el backend calcula h1", "A_PEDRO", "h1", false),
			pendiente("María cambió su configuración: el backend calcula h2 y el reloj sigue en h1", "A_MARIA", "h2",
					true),
			c.acepta("El reloj de María aplica la configuración nueva y la informa en su siguiente lote", """
					INSERT INTO sincronizacion (id_sincronizacion, event_id, id_asignacion, hash_configuracion,
					fecha_hora_recepcion, nivel_bateria)
					VALUES (gen_random_uuid(), gen_random_uuid(), '{A_MARIA}', 'h2', '2026-10-15 17:01Z', 68)"""),
			pendiente("María al día", "A_MARIA", "h2", false),
			pendiente("Un reloj que aún no envía lotes queda pendiente", "INEXISTENTE", "h1", true));
	}

	private DynamicContainer flujoDeUnaAlerta() {
		return grupo("10. Flujo completo de una alerta (caída de María)",
			c.acepta("Llega una caída MEDIA del reloj, con su evidencia",
					alertaReloj("CAIDA_MARIA", "A_MARIA", "CAIDA", "MEDIA", ", evidencia, fecha_hora_envio",
					", '{\"aceleracion\": 3.2, \"version_matriz\": \"2026-10\"}', '2026-10-15 03:00:30-03'")),
			estado("Estado inicial", "CAIDA_MARIA", "RECIBIDA", "SIN_ASIGNAR"),
			c.vale("Se asigna un folio automáticamente",
					"SELECT numero IS NOT NULL n FROM alerta WHERE id_alerta = '{CAIDA_MARIA}'", true),
			c.vale("Retraso por cobertura (recepción menos detección) se calcula", """
					SELECT fecha_hora_recepcion - fecha_hora_deteccion > interval '0' n FROM alerta
					WHERE id_alerta = '{CAIDA_MARIA}'""", true),
			c.acepta("El reloj pide avisar a la hija (prioridad 1)",
					aviso("CAIDA_MARIA", "M1", 1, ", id_externo", ", 'wamid.1'")),
			estado("Estado al enviar el primer aviso", "CAIDA_MARIA", "AVISANDO_CONTACTO", "SIN_ASIGNAR"),
			c.acepta("Meta informa entrega y lectura (webhook)", """
					UPDATE registro_intento SET fecha_hora_entrega = now(), fecha_hora_lectura = now()
					WHERE id_externo = 'wamid.1'"""),
			c.vale("Estado de entrega calculado desde las fechas", """
					SELECT CASE WHEN fecha_hora_fallo IS NOT NULL THEN 'FALLIDO' WHEN fecha_hora_lectura IS NOT NULL
					THEN 'LEIDO' WHEN fecha_hora_entrega IS NOT NULL THEN 'ENTREGADO' ELSE 'ENVIADO' END n
					FROM registro_intento WHERE id_externo = 'wamid.1'""", "LEIDO"),
			c.acepta("La hija responde \"No puedo\"", """
					UPDATE registro_intento SET respuesta = 'NO_PUEDO', fecha_hora_respuesta = now()
					WHERE id_externo = 'wamid.1'"""),
			estado("Sigue avisando", "CAIDA_MARIA", "AVISANDO_CONTACTO", "SIN_ASIGNAR"),
			c.acepta("La caída se agrava por inmovilidad",
					"UPDATE alerta SET fecha_hora_agravamiento = now() WHERE id_alerta = '{CAIDA_MARIA}'"),
			c.vale("Conserva la gravedad con que llegó",
					"SELECT severidad n FROM alerta WHERE id_alerta = '{CAIDA_MARIA}'", "MEDIA"),
			c.vale("Gravedad actual calculada",
					"SELECT severidad_actual n FROM alerta_estado WHERE id_alerta = '{CAIDA_MARIA}'", "GRAVE"),
			c.acepta("El reloj pasa a Seguridad Ciudadana (prioridad 2), que responde \"Voy en camino\"",
					aviso("CAIDA_MARIA", "M2", 1, ", id_externo, respuesta, fecha_hora_respuesta",
							", 'wamid.2', 'VOY_EN_CAMINO', now()")),
			estado("Estado tras la confirmación", "CAIDA_MARIA", "CONFIRMADA", "SIN_ASIGNAR"),
			c.acepta("Aviso FCM al reloj y el reloj informa que lo recibió", """
					INSERT INTO aviso_fcm (id_aviso, id_alerta, tipo, fecha_hora_envio, intentos)
					VALUES (gen_random_uuid(), '{CAIDA_MARIA}', 'ALERTA_CONFIRMADA', now(), 1);
					UPDATE aviso_fcm SET fecha_hora_recepcion = now() WHERE id_alerta = '{CAIDA_MARIA}'"""),
			c.vale("Contacto que confirmó (para la pantalla del reloj)", """
					SELECT k.nombre n FROM registro_intento i JOIN contacto_paciente cp USING (id_contacto_paciente)
					JOIN contacto k USING (id_contacto)
					WHERE i.id_alerta = '{CAIDA_MARIA}' AND i.respuesta = 'VOY_EN_CAMINO'""", "Seguridad Ciudadana"),
			c.filas("Ana asume el caso", asumir("CAIDA_MARIA", "ANA", "2026-10-15 08:05Z"), 1),
			estado("Estado al asumir", "CAIDA_MARIA", "CONFIRMADA", "EN_ATENCION"),
			c.filas("Luis intenta asumirlo un momento después: no afecta filas (ya lo tomó Ana)",
					asumir("CAIDA_MARIA", "LUIS", "2026-10-15 08:06Z"), 0),
			c.vale("La responsable sigue siendo Ana", """
					SELECT nombre_profesional n FROM atencion_alerta JOIN usuario ON id_usuario = id_usuario_responsable
					WHERE id_alerta = '{CAIDA_MARIA}'""", "Ana"),
			c.acepta("Ana la resuelve como POSITIVA y queda a cargo del seguimiento", """
					UPDATE atencion_alerta SET fecha_hora_cierre = '2026-10-15 09:00Z', id_usuario_resolutor = '{ANA}',
					clasificacion = 'POSITIVA', observaciones_cierre = 'Caída real, sin lesiones'
					WHERE id_alerta = '{CAIDA_MARIA}'"""),
			estado("Estado tras el cierre", "CAIDA_MARIA", "RESUELTA", "EN_SEGUIMIENTO"),
			c.acepta("Reasignación del seguimiento a Luis",
					"UPDATE atencion_alerta SET id_usuario_responsable = '{LUIS}' WHERE id_alerta = '{CAIDA_MARIA}'"),
			c.vale("Destinos de derivación iniciales de la migración",
					"SELECT count(*) n FROM destino_derivacion WHERE codigo IN ('CESFAM', 'SAMU')", 2),
			c.acepta("Llamada y derivación al CESFAM", """
			INSERT INTO seguimiento (id_seguimiento, id_alerta, id_usuario, tipo, codigo_destino, nota, fecha_hora)
					VALUES
					(gen_random_uuid(), '{CAIDA_MARIA}', '{LUIS}', 'LLAMADA', null, 'Se llama a la hija',
					'2026-10-16 10:00Z'),
					(gen_random_uuid(), '{CAIDA_MARIA}', '{LUIS}', 'DERIVACION', 'CESFAM', 'Control kinesiológico',
					'2026-10-16 11:00Z')"""),
			c.vale("\"Derivada\" se ve en el seguimiento (no es un estado)", """
					SELECT count(*) n FROM seguimiento WHERE id_alerta = '{CAIDA_MARIA}' AND tipo = 'DERIVACION'""", 1),
			c.rechaza("Derivación sin destino", """
					INSERT INTO seguimiento (id_seguimiento, id_alerta, id_usuario, tipo, fecha_hora)
					VALUES (gen_random_uuid(), '{CAIDA_MARIA}', '{LUIS}', 'DERIVACION', now())""", CHECK),
			c.rechaza("Llamada con destino de derivación", """
					INSERT INTO seguimiento (id_seguimiento, id_alerta, id_usuario, tipo, codigo_destino, fecha_hora)
					VALUES (gen_random_uuid(), '{CAIDA_MARIA}', '{LUIS}', 'LLAMADA', 'SAMU', now())""", CHECK),
			c.rechaza("Derivación a un destino que no existe", """
					INSERT INTO seguimiento (id_seguimiento, id_alerta, id_usuario, tipo, codigo_destino, fecha_hora)
					VALUES (gen_random_uuid(), '{CAIDA_MARIA}', '{LUIS}', 'DERIVACION', 'BOMBEROS', now())""", FORANEA),
			c.rechaza("Cierre del seguimiento antes del cierre de la alerta", """
					UPDATE atencion_alerta SET fecha_cierre_seguimiento = '2026-10-15 08:00Z'
					WHERE id_alerta = '{CAIDA_MARIA}'""", CHECK),
			c.acepta("Cierre del seguimiento", """
					UPDATE atencion_alerta SET fecha_cierre_seguimiento = '2026-10-17 12:00Z'
					WHERE id_alerta = '{CAIDA_MARIA}'"""),
			estado("Estado final", "CAIDA_MARIA", "RESUELTA", "RESUELTA"),
			c.backend("Agregar un seguimiento después del cierre del seguimiento", """
					INSERT INTO seguimiento (id_seguimiento, id_alerta, id_usuario, tipo, fecha_hora)
					VALUES (gen_random_uuid(), '{CAIDA_MARIA}', '{LUIS}', 'NOTA', '2026-10-18 10:00Z')"""),
			c.vale("Historial del caso (avisos + FCM + seguimientos)", """
					SELECT (SELECT count(*) FROM registro_intento WHERE id_alerta = '{CAIDA_MARIA}')
					+ (SELECT count(*) FROM aviso_fcm WHERE id_alerta = '{CAIDA_MARIA}')
					+ (SELECT count(*) FROM seguimiento WHERE id_alerta = '{CAIDA_MARIA}') n""", 5),
			// Se controla con permisos de la base; las pruebas corren como dueño, por eso aquí se acepta
			c.backend("El equipo cambia la gravedad con que llegó la alerta",
					"UPDATE alerta SET severidad = 'LEVE' WHERE id_alerta = '{CAIDA_MARIA}'"));
	}

	private DynamicContainer reglasDeLaAtencion() {
		return grupo("11. Reglas de la atención (atencion_alerta)",
			c.acepta("Media de madrugada (Rosa): avisa de inmediato a Seguridad Ciudadana (compartido)",
					alertaReloj("MEDIA_ROSA", "A_ROSA", "FC_SPO2", "MEDIA") + ";" + aviso("MEDIA_ROSA", "R1", 1)),
			c.rechaza("Atención vacía (sin toma ni cierre)", atencion("MEDIA_ROSA", "", ""), CHECK),
			c.rechaza("Toma sin responsable", atencion("MEDIA_ROSA", ", fecha_hora_toma", ", now()"), CHECK),
			c.rechaza("Responsable sin hora de toma",
					atencion("MEDIA_ROSA", ", id_usuario_responsable", ", '{ANA}'"), CHECK),
			c.rechaza("Clasificar sin cerrar", atencion("MEDIA_ROSA",
					", id_usuario_responsable, fecha_hora_toma, clasificacion", ", '{ANA}', now(), 'FALSA_ALARMA'"),
					CHECK),
			c.rechaza("Observaciones de cierre sin cerrar", atencion("MEDIA_ROSA",
					", id_usuario_responsable, fecha_hora_toma, observaciones_cierre", ", '{ANA}', now(), 'x'"), CHECK),
			c.rechaza("Cierre anterior a la toma", atencion("MEDIA_ROSA",
					", id_usuario_responsable, fecha_hora_toma, fecha_hora_cierre",
					", '{ANA}', '2026-10-15 10:00Z', '2026-10-15 09:00Z'"), CHECK),
			c.rechaza("POSITIVA sin responsable del seguimiento", atencion("MEDIA_ROSA",
					", fecha_hora_cierre, id_usuario_resolutor, clasificacion", ", now(), '{ANA}', 'POSITIVA'"), CHECK),
			c.rechaza("Cierre de seguimiento en una falsa alarma", atencion("MEDIA_ROSA",
					", fecha_hora_cierre, clasificacion, fecha_cierre_seguimiento", ", now(), 'FALSA_ALARMA', now()"),
					CHECK),
			c.rechaza("Responsable que no existe", atencion("MEDIA_ROSA",
					", id_usuario_responsable, fecha_hora_toma", ", gen_random_uuid(), now()"), FORANEA),
			c.rechaza("Clasificación que no existe", atencion("MEDIA_ROSA",
					", fecha_hora_cierre, clasificacion", ", now(), 'DUDOSA'"), CHECK),
			c.rechaza("Dos atenciones para la misma alerta", atencion("CAIDA_MARIA",
					", id_usuario_responsable, fecha_hora_toma", ", '{ANA}', now()"), UNICO),
			c.rechaza("Atención de una alerta que no existe", atencion("ALERTA_INEXISTENTE",
					", id_usuario_responsable, fecha_hora_toma", ", '{ANA}', now()"), FORANEA));
	}

	private DynamicContainer gravedadYHorario() {
		return grupo("12. Gravedad y horario (leve, media y grave de madrugada)",
			c.acepta("Leve de madrugada (Pedro): el reloj no avisa y espera el día hábil",
					alertaReloj("LEVE_PEDRO", "A_PEDRO", "FC", "LEVE", ", espera_dia_habil", ", true")),
			estado("Queda en la bandeja de día hábil", "LEVE_PEDRO", "PENDIENTE_DIA_HABIL", "SIN_ASIGNAR"),
			c.vale("El folio crece con cada alerta", """
					SELECT b.numero > a.numero n FROM alerta a, alerta b
					WHERE a.id_alerta = '{CAIDA_MARIA}' AND b.id_alerta = '{LEVE_PEDRO}'""", true),
			c.acepta("A primera hora, Ana la cierra directamente como falsa alarma (sin asumirla antes)",
					atencion("LEVE_PEDRO",
							", fecha_hora_cierre, id_usuario_resolutor, clasificacion, observaciones_cierre",
							", now(), '{ANA}', 'FALSA_ALARMA', 'Lectura por movimiento'")),
			estado("Estado tras la revisión", "LEVE_PEDRO", "RESUELTA", "RESUELTA"),
			estado("La media está avisando", "MEDIA_ROSA", "AVISANDO_CONTACTO", "SIN_ASIGNAR"),
			c.acepta("Grave de madrugada (Rosa): el reloj recorre la lista dos veces", String.join(";",
					alertaReloj("GRAVE_ROSA", "A_ROSA", "SPO2", "GRAVE"),
					aviso("GRAVE_ROSA", "R1", 1), aviso("GRAVE_ROSA", "R2", 1, "", "", "CORREO"),
					aviso("GRAVE_ROSA", "R1", 2), aviso("GRAVE_ROSA", "R2", 2, "", "", "CORREO"))),
			c.acepta("Nadie confirma: el reloj informa el fin de la lista",
					"UPDATE alerta SET fecha_hora_sin_respuesta = now() WHERE id_alerta = '{GRAVE_ROSA}'"),
			estado("Estado de la grave sin respuesta", "GRAVE_ROSA", "SIN_RESPUESTA", "SIN_ASIGNAR"),
			c.rechaza("Una media marcada para esperar el día hábil",
					alertaReloj("ALERTA_X1", "A_ROSA", "FC", "MEDIA", ", espera_dia_habil", ", true"), CHECK),
			c.rechaza("Una grave marcada para esperar el día hábil",
					alertaReloj("ALERTA_X2", "A_ROSA", "FC", "GRAVE", ", espera_dia_habil", ", true"), CHECK),
			c.rechaza("Gravedad que no existe (MODERADA)", alertaReloj("ALERTA_X3", "A_ROSA", "FC", "MODERADA"), CHECK),
			c.rechaza("Agravar una alerta que llegó grave",
					"UPDATE alerta SET fecha_hora_agravamiento = now() WHERE id_alerta = '{GRAVE_ROSA}'", CHECK),
			c.rechaza("Agravar una alerta que no es caída",
					"UPDATE alerta SET fecha_hora_agravamiento = now() WHERE id_alerta = '{MEDIA_ROSA}'", CHECK),
			c.rechaza("Tercera ronda de avisos", aviso("GRAVE_ROSA", "R1", 3), CHECK));
	}

	private DynamicContainer cancelacionYSimulacro() {
		return grupo("13. Cancelación por el paciente y simulacro",
			c.acepta("El paciente cancela una caída en los 30 segundos",
					alertaReloj("CANCELADA", "A_PEDRO", "CAIDA", "GRAVE", ", fecha_hora_cancelacion", ", now()")),
			estado("Estado de la cancelada", "CANCELADA", "CANCELADA", "SIN_ASIGNAR"),
			c.rechaza("Una cancelada no puede quedar sin respuesta",
					"UPDATE alerta SET fecha_hora_sin_respuesta = now() WHERE id_alerta = '{CANCELADA}'", CHECK),
			c.backend("Una cancelada recibe atención del equipo",
					atencion("CANCELADA", ", id_usuario_responsable, fecha_hora_toma", ", '{ANA}', now()")),
			c.backend("Una cancelada tiene avisos a contactos", aviso("CANCELADA", "P2", 1)),
			c.acepta("Simulacro desde el dashboard, con aviso FCM",
					alertaServidor("SIMULACRO", "A_PEDRO", "SILENCIO_DATOS", ", es_simulacro", ", true")
					+ "; INSERT INTO aviso_fcm (id_aviso, id_alerta, tipo)"
					+ " VALUES (gen_random_uuid(), '{SIMULACRO}', 'SIMULACRO')"),
			c.rechaza("Avisos FCM con intentos negativos",
					"UPDATE aviso_fcm SET intentos = -1 WHERE id_alerta = '{SIMULACRO}'", CHECK));
	}

	private DynamicContainer alertasTecnicas() {
		return grupo("14. Alertas técnicas del servidor",
			c.acepta("Bloqueo de la nota de alerta: pg_advisory_xact_lock(hashtextextended(...))", """
					BEGIN;
			SELECT pg_advisory_xact_lock(hashtextextended('{A_PEDRO}'::uuid::text || ':' || 'SILENCIO_DATOS', 0));
					COMMIT"""),
			c.acepta("Silencio de datos del reloj de Pedro", alertaServidor("SILENCIO", "A_PEDRO", "SILENCIO_DATOS")),
			c.vale("Hay un silencio de datos abierto: el job no crea otro",
					abiertaServidor("A_PEDRO", "SILENCIO_DATOS"), 1),
			c.acepta("Vuelven los datos: el sistema la cierra solo (sin profesional)",
					atencion("SILENCIO", ", fecha_hora_cierre", ", '2026-10-16 10:00Z'")),
			estado("Estado de la técnica cerrada por el sistema", "SILENCIO", "RESUELTA", "RESUELTA"),
			c.vale("Ya no hay silencio abierto", abiertaServidor("A_PEDRO", "SILENCIO_DATOS"), 0),
			c.filas("Alguien intenta asumir la técnica ya cerrada con la receta de la nota: no afecta filas",
					asumir("SILENCIO", "ANA", "2026-10-16 11:00Z"), 0),
			c.acepta("Después puede abrirse un nuevo silencio de datos",
					alertaServidor("SILENCIO_2", "A_PEDRO", "SILENCIO_DATOS")),
			c.acepta("El reloj envía otra alerta del mismo tipo con una abierta (nunca se descarta)",
					alertaReloj("GRAVE_ROSA_2", "A_ROSA", "SPO2", "GRAVE")),
			c.backend("Clasificar como POSITIVA una alerta técnica", atencion("SILENCIO_2",
					", id_usuario_responsable, fecha_hora_toma, fecha_hora_cierre, clasificacion",
					", '{ANA}', now(), now(), 'POSITIVA'")),
			c.rechaza("Alerta del servidor que dice traer configuración del reloj", """
					INSERT INTO alerta (id_alerta, event_id, id_asignacion, hash_configuracion, origen, tipo_evento,
					severidad, fecha_hora_recepcion) VALUES (gen_random_uuid(), gen_random_uuid(), '{A_PEDRO}', 'h1',
					'SERVIDOR', 'BATERIA_BAJA', 'LEVE', now())""", CHECK),
			c.rechaza("Alerta del reloj sin el hash de su configuración", """
					INSERT INTO alerta (id_alerta, event_id, id_asignacion, origen, tipo_evento, severidad,
					fecha_hora_recepcion)
					VALUES (gen_random_uuid(), gen_random_uuid(), '{A_PEDRO}', 'RELOJ', 'FC', 'LEVE', now())""", CHECK),
			c.rechaza("El servidor crea una alerta clínica (CAIDA)", alertaServidor("ALERTA_X4", "A_PEDRO", "CAIDA"),
					CHECK),
			c.acepta("El servidor crea una alerta de batería baja (técnica, se deshace)",
					"BEGIN; " + alertaServidor("ALERTA_X4", "A_PEDRO", "BATERIA_BAJA") + "; ROLLBACK"),
			c.rechaza("El reloj informa un silencio de datos (solo lo puede detectar el servidor)",
					alertaReloj("ALERTA_X5", "A_PEDRO", "SILENCIO_DATOS", "LEVE"), CHECK));
	}

	private DynamicContainer idempotencia() {
		String reenvio = """
				INSERT INTO alerta (id_alerta, event_id, id_asignacion, hash_configuracion, origen, tipo_evento,
				severidad, fecha_hora_recepcion)
				SELECT gen_random_uuid(), event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad,
				now() FROM alerta WHERE id_alerta = '{CAIDA_MARIA}'""";
		return grupo("15. Reintentos y mensajes duplicados (idempotencia)",
			c.rechaza("El reloj reenvía la misma alerta (mismo event_id)", reenvio, UNICO),
			c.filas("El backend ignora la alerta reenviada (ON CONFLICT (event_id) DO NOTHING)",
					reenvio + " ON CONFLICT (event_id) DO NOTHING", 0),
			c.rechaza("El reloj repite una solicitud de aviso (no se envía dos veces)", """
					INSERT INTO registro_intento (id_intento, event_id, id_alerta, id_contacto_paciente, canal, ronda)
					SELECT gen_random_uuid(), event_id, id_alerta, id_contacto_paciente, canal, ronda
					FROM registro_intento WHERE id_externo = 'wamid.1'""", UNICO),
			c.rechaza("Meta repite un webhook (mismo id de mensaje)",
					aviso("CAIDA_MARIA", "M1", 2, ", id_externo", ", 'wamid.1'"), UNICO),
			c.rechaza("Aviso FCM repetido del mismo tipo para la misma alerta", """
					INSERT INTO aviso_fcm (id_aviso, id_alerta, tipo)
					VALUES (gen_random_uuid(), '{CAIDA_MARIA}', 'ALERTA_CONFIRMADA')""", UNICO),
			c.rechaza("Dos solicitudes distintas para el mismo contacto, canal y ronda (el reloj se reinició)",
					aviso("MEDIA_ROSA", "R1", 1), UNICO),
			c.acepta("El mismo contacto en la ronda 2 (se deshace)",
					"BEGIN; " + aviso("MEDIA_ROSA", "R1", 2) + "; ROLLBACK"));
	}

	private DynamicContainer avisosAContactos() {
		return grupo("16. Reglas de los avisos a contactos",
			c.rechaza("Respuesta del contacto sin su hora",
					aviso("MEDIA_ROSA", "R2", 2, ", respuesta", ", 'NO_PUEDO'"), CHECK),
			c.rechaza("Motivo de falla sin fecha de falla",
					aviso("MEDIA_ROSA", "R2", 2, ", motivo_fallo", ", 'Número inválido'"), CHECK),
			c.rechaza("Lectura informada en un correo (solo WhatsApp)",
					aviso("MEDIA_ROSA", "R2", 2, ", fecha_hora_lectura", ", now()", "CORREO"), CHECK),
			c.rechaza("Respuesta que no existe en los botones",
					aviso("MEDIA_ROSA", "R2", 2, ", respuesta, fecha_hora_respuesta", ", 'TAL_VEZ', now()"), CHECK),
			c.rechaza("Canal que no existe (SMS)", aviso("MEDIA_ROSA", "R2", 2, "", "", "SMS"), CHECK),
			c.rechaza("Aviso a un vínculo que no existe", aviso("MEDIA_ROSA", "INEXISTENTE", 1), FORANEA),
			c.backend("Aviso a un vínculo de otro paciente (la hija de María en una alerta de Rosa)",
					aviso("MEDIA_ROSA", "M1", 2)),
			c.backend("Aviso por WhatsApp a una persona que solo tiene correo (el vecino)",
					aviso("MEDIA_ROSA", "R2", 2)),
			c.backend("Aviso por WhatsApp a una persona que revocó el opt-in (la cuidadora de Pedro)",
					aviso("LEVE_PEDRO", "P1", 1)));
	}

	private DynamicContainer datosCalculados() {
		return grupo("17. Datos calculados (reportes, hallazgos, panel)",
			c.vale("Reporte diario de María (hora de Chile): FC promedio / mínima / máxima", """
					SELECT round(avg(valor)) || ' / ' || min(valor) || ' / ' || max(valor) n FROM registro_vital r
					JOIN asignacion_reloj a USING (id_asignacion)
					WHERE a.id_paciente = '{MARIA}' AND r.tipo = 'FC'
			AND (r.fecha_hora_medicion AT TIME ZONE 'America/Santiago')::date = '2026-10-15'""", "80 / 70 / 90"),
			c.vale("Uso del reloj de María (minutos puesto)",
					"SELECT coalesce(sum(minutos_puesto), 0) n FROM sincronizacion WHERE id_asignacion = '{A_MARIA}'",
					110),
			c.vale("Hallazgo de la red de seguridad: SpO2 bajo 90 sin alerta cercana del reloj", """
					SELECT count(*) n FROM registro_vital r WHERE r.tipo = 'SPO2' AND r.valor < 90
			AND NOT EXISTS (SELECT 1 FROM alerta al WHERE al.id_asignacion = r.id_asignacion AND al.origen = 'RELOJ'
					AND al.fecha_hora_deteccion BETWEEN r.fecha_hora_medicion - interval '30 minutes'
					AND r.fecha_hora_medicion + interval '30 minutes')""", 1),
			c.vale("Métrica de validación: SpO2 descartados por los relojes",
					"SELECT sum(cantidad) n FROM evento_descartado WHERE tipo_evento = 'SPO2'", 5),
			c.vale("Métricas: alertas reales (sin simulacros ni canceladas)",
					"SELECT count(*) n FROM alerta WHERE NOT es_simulacro AND fecha_hora_cancelacion IS NULL", 7),
			c.vale("Panel: alertas reales abiertas", """
					SELECT count(*) n FROM alerta_estado e JOIN alerta a USING (id_alerta)
			WHERE NOT a.es_simulacro AND e.estado_contacto <> 'CANCELADA' AND e.estado_atencion <> 'RESUELTA'""",
					4),
			c.vale("Panel: alertas sin respuesta (prioridad máxima)",
					"SELECT count(*) n FROM alerta_estado WHERE estado_contacto = 'SIN_RESPUESTA'", 1),
			c.vale("Panel: casos en seguimiento",
					"SELECT count(*) n FROM alerta_estado WHERE estado_atencion = 'EN_SEGUIMIENTO'", 0),
			c.vale("Inventario: relojes vigentes con batería bajo 20 % en su último lote", """
					SELECT count(*) n FROM (SELECT DISTINCT ON (s.id_asignacion) s.nivel_bateria FROM sincronizacion s
					JOIN asignacion_reloj a USING (id_asignacion) WHERE a.fecha_fin IS NULL
					ORDER BY s.id_asignacion, s.fecha_hora_recepcion DESC) u WHERE nivel_bateria < 20""", 1));
	}

	private DynamicContainer auditoria() {
		String configuracionMaria = "'{MARIA}|2026-10-10 12:00Z'";
		return grupo("18. Auditoría de cambios",
			c.acepta("Ana modifica a María y cambia su configuración; queda en la auditoría",
					"INSERT INTO registro_auditoria (id_auditoria, id_usuario, accion, entidad, id_entidad, detalle,"
					+ " fecha_hora) VALUES"
					+ " (gen_random_uuid(), '{ANA}', 'MODIFICAR', 'PACIENTE', '{MARIA}',"
					+ " '{\"campo\": \"nivel_dependencia\"}', now()),"
					+ " (gen_random_uuid(), '{ANA}', 'CREAR', 'CONFIGURACION', " + configuracionMaria
					+ ", null, now()),"
					+ " (gen_random_uuid(), '{ANA}', 'MODIFICAR', 'ROL', 'COORDINADOR',"
					+ " '{\"permiso\": \"REPORTES/EXPORTAR\"}', now())"),
			c.acepta("Auditar un contacto de la lista, la ficha, una condición y un permiso, cada uno con su id", """
					INSERT INTO registro_auditoria (id_auditoria, id_usuario, accion, entidad, id_entidad, fecha_hora)
					VALUES
					(gen_random_uuid(), '{ANA}', 'MODIFICAR', 'CONTACTO_PACIENTE', '{P3}', now()),
					(gen_random_uuid(), '{ANA}', 'MODIFICAR', 'FICHA_PACIENTE', '{PEDRO}', now()),
					(gen_random_uuid(), '{ANA}', 'CREAR', 'CONDICION_PACIENTE', gen_random_uuid()::text, now()),
					(gen_random_uuid(), '{ADMIN}', 'ELIMINAR', 'PERMISO_ROL', 'PERSONAL|ALERTAS|EDITAR', now())"""),
			c.vale("Historial de un objeto con clave compuesta (configuración de María del 10-10)",
					"SELECT count(*) n FROM registro_auditoria WHERE entidad = 'CONFIGURACION' AND id_entidad = "
					+ configuracionMaria, 1),
			c.rechaza("Borrar a una profesional con historial (se desactiva, no se borra)",
					"DELETE FROM usuario WHERE id_usuario = '{ANA}'", FORANEA));
	}

	private DynamicContainer borradoAlFinDelPiloto() {
		return grupo("19. Borrado al terminar el piloto (4.4.5)",
			c.rechaza("Borrar un reloj asignado", "DELETE FROM smartwatch WHERE id_dispositivo = '{W1}'", FORANEA),
			c.rechaza("Borrar un vínculo que tiene avisos enviados",
					"DELETE FROM contacto_paciente WHERE id_contacto_paciente = '{M1}'", FORANEA),
			c.rechaza("Borrar una persona que es contacto de pacientes",
					"DELETE FROM contacto WHERE id_contacto = '{SEG_CIUDADANA}'", FORANEA),
			c.acepta("Borrar a María con todos sus datos", "DELETE FROM paciente WHERE id_paciente = '{MARIA}'"),
			c.vale("No queda ningún dato de María", """
					SELECT (SELECT count(*) FROM ficha_paciente WHERE id_paciente = '{MARIA}')
					+ (SELECT count(*) FROM condicion_paciente WHERE id_paciente = '{MARIA}')
					+ (SELECT count(*) FROM documento WHERE id_paciente = '{MARIA}')
					+ (SELECT count(*) FROM contacto_paciente WHERE id_paciente = '{MARIA}')
					+ (SELECT count(*) FROM configuracion WHERE id_paciente = '{MARIA}')
					+ (SELECT count(*) FROM umbral WHERE id_paciente = '{MARIA}')
					+ (SELECT count(*) FROM asignacion_reloj WHERE id_paciente = '{MARIA}')
					+ (SELECT count(*) FROM sincronizacion WHERE id_asignacion = '{A_MARIA}')
					+ (SELECT count(*) FROM registro_vital WHERE id_asignacion = '{A_MARIA}')
					+ (SELECT count(*) FROM ubicacion WHERE id_asignacion = '{A_MARIA}')
					+ (SELECT count(*) FROM evento_descartado WHERE id_sincronizacion = '{S_MARIA}')
					+ (SELECT count(*) FROM alerta WHERE id_asignacion = '{A_MARIA}')
					+ (SELECT count(*) FROM atencion_alerta WHERE id_alerta = '{CAIDA_MARIA}')
					+ (SELECT count(*) FROM registro_intento WHERE id_alerta = '{CAIDA_MARIA}')
					+ (SELECT count(*) FROM aviso_fcm WHERE id_alerta = '{CAIDA_MARIA}')
					+ (SELECT count(*) FROM seguimiento WHERE id_alerta = '{CAIDA_MARIA}') n""", 0),
			c.vale("Pedro y Rosa conservan sus datos (5 vínculos + 8 alertas)", """
					SELECT (SELECT count(*) FROM contacto_paciente WHERE id_paciente IN ('{PEDRO}', '{ROSA}'))
					+ (SELECT count(*) FROM alerta a JOIN asignacion_reloj r USING (id_asignacion)
					WHERE r.id_paciente IN ('{PEDRO}', '{ROSA}')) n""", 13),
			c.vale("La auditoría sobre María sobrevive al borrado",
					"SELECT count(*) n FROM registro_auditoria WHERE id_entidad LIKE '{MARIA}%'", 2),
			c.vale("Seguridad Ciudadana se conserva (sigue siendo contacto de Pedro y Rosa)",
					"SELECT count(*) n FROM contacto WHERE id_contacto = '{SEG_CIUDADANA}'", 1),
			c.acepta("Limpieza: el backend borra las personas que quedaron sin pacientes", """
					DELETE FROM contacto k WHERE NOT EXISTS
					(SELECT 1 FROM contacto_paciente cp WHERE cp.id_contacto = k.id_contacto)"""),
			c.vale("Se borró solo la hija de María", """
					SELECT count(*) || ' / ' || count(*) FILTER (WHERE id_contacto = '{HIJA}') n FROM contacto""",
					"3 / 0"),
			c.vale("Se conservan profesionales y relojes", """
					SELECT (SELECT count(*) FROM usuario) || ' / ' || (SELECT count(*) FROM smartwatch) n""", "4 / 5"));
	}

}
