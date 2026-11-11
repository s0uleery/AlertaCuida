// Pruebas del modelo de datos de AlertaCuida (tarea 0.4).
// Crea la base desde ../modelo-datos.dbml en un PostgreSQL embebido (PGlite), aplica reglas-extra.sql,
// recorre los casos y escribe el informe en ../pruebas-modelo-datos.md.
// Uso: npm install && npm test

import { readFileSync, writeFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { exporter } from '@dbml/core';
import { PGlite } from '@electric-sql/pglite';
import { btree_gist } from '@electric-sql/pglite/contrib/btree_gist';

const aqui = dirname(fileURLToPath(import.meta.url));
const leer = (ruta) => readFileSync(join(aqui, ruta), 'utf8');
const versionDe = (paquete) => JSON.parse(leer(`node_modules/${paquete}/package.json`)).version;

// ---------- Preparación ----------
const sqlModelo = exporter.export(leer('../modelo-datos.dbml'), 'postgres');
const reglas = leer('reglas-extra.sql');
const crearBase = async () => { const base = new PGlite({ extensions: { btree_gist } }); await base.exec(sqlModelo); await base.exec(reglas); return base; };

// El grupo A usa una base aparte: revisa las relaciones uno a uno con datos propios
const dbUnoAUno = await crearBase();
const dbPrincipal = await crearBase();
let db = dbUnoAUno;

// ---------- Registro de casos ----------
const resultados = [];
let grupo = '';
const nuevoGrupo = (nombre) => { grupo = nombre; };
// estado: ok ✅ | falla ❌ | hueco ⚠️ (la base lo permite y no debería) | backend ℹ️ (el modelo lo deja al backend)
const anotar = (caso, esperado, estado, obtenido, nota = '') => resultados.push({ grupo, caso, esperado, estado, obtenido, nota });

const MOTIVOS = {
  unico: { texto: 'clave única', patron: /duplicate key value violates unique constraint/ },
  check: { texto: 'CHECK', patron: /violates check constraint/ },
  foranea: { texto: 'clave foránea', patron: /violates foreign key constraint/ },
  nulo: { texto: 'NOT NULL', patron: /violates not-null constraint/ },
  exclusion: { texto: 'superposición (EXCLUDE)', patron: /violates exclusion constraint/ },
  enum: { texto: 'valor fuera del enum', patron: /invalid input value for enum/ },
  disparador: { texto: 'disparador (como un CHECK)', patron: /contacto interno|de interno a externo/ },
};

// Debe guardarse sin error
async function acepta(caso, sql) {
  try { await db.exec(sql); anotar(caso, 'Se acepta', 'ok', 'Aceptado'); }
  catch (e) { await db.exec('ROLLBACK'); anotar(caso, 'Se acepta', 'falla', `Rechazado: ${e.message}`); }
}

// Debe rechazarse, y por el motivo indicado. Se deshace siempre.
async function rechaza(caso, sql, motivo) {
  const m = MOTIVOS[motivo];
  const esperado = `Se rechaza (${m.texto})`;
  try {
    await db.exec(`BEGIN; ${sql}; SET CONSTRAINTS ALL IMMEDIATE;`);
    await db.exec('ROLLBACK');
    anotar(caso, esperado, 'falla', 'Aceptado');
  } catch (e) {
    await db.exec('ROLLBACK');
    const ok = m.patron.test(e.message);
    anotar(caso, esperado, ok ? 'ok' : 'falla', ok ? `Rechazado (${m.texto})` : `Rechazado por otro motivo: ${e.message}`);
  }
}

// Ejecuta sin guardar (se deshace siempre) y devuelve si la base lo aceptó
async function probarSinGuardar(sql) {
  try { await db.exec(`BEGIN; ${sql}; SET CONSTRAINTS ALL IMMEDIATE;`); await db.exec('ROLLBACK'); return { aceptado: true }; }
  catch (e) { await db.exec('ROLLBACK'); return { aceptado: false, error: e.message }; }
}

// Algo que el modelo debería impedir (o permitir) y hoy no lo hace. No hace fallar la ejecución.
async function hueco(caso, sql, recomendacion, hoySeAcepta = true) {
  const r = await probarSinGuardar(sql);
  const sigue = r.aceptado === hoySeAcepta;
  anotar(caso, hoySeAcepta ? 'Debería rechazarse' : 'Debería aceptarse', sigue ? 'hueco' : 'ok',
    r.aceptado ? 'Aceptado' : `Rechazado: ${r.error}`, sigue ? recomendacion : '');
}

// La base lo acepta a propósito: la nota del modelo dice que lo valida el backend
async function backend(caso, sql, nota) {
  const r = await probarSinGuardar(sql);
  anotar(caso, 'Lo valida el backend', r.aceptado ? 'backend' : 'ok', r.aceptado ? 'Aceptado por la base' : `Rechazado: ${r.error}`, nota);
}

// Una consulta debe devolver el valor esperado (columna "n")
async function vale(caso, sql, esperado) {
  try {
    const n = (await db.query(sql)).rows[0]?.n;
    anotar(caso, `= ${esperado}`, String(n) === String(esperado) ? 'ok' : 'falla', `= ${n}`);
  } catch (e) { anotar(caso, `= ${esperado}`, 'falla', `Error: ${e.message}`); }
}

// Una sentencia debe afectar esa cantidad de filas
async function filas(caso, sql, esperado) {
  const texto = `${esperado} fila(s) afectada(s)`;
  try {
    const n = (await db.query(sql)).affectedRows ?? 0;
    anotar(caso, texto, n === esperado ? 'ok' : 'falla', `${n} fila(s) afectada(s)`);
  } catch (e) { anotar(caso, texto, 'falla', `Error: ${e.message}`); }
}

// ---------- Datos de prueba ----------
const id = (n) => '00000000-0000-0000-0000-' + String(n).padStart(12, '0');
const ANA = id(1), LUIS = id(2), ADMIN = id(3), NUEVA = id(4);      // profesionales
const MARIA = id(11), PEDRO = id(12), ROSA = id(13);                // pacientes
const HIJA = id(21), SEG_CIUDADANA = id(22), VECINO = id(23), CUIDADORA = id(24); // personas
const INTERNO_ANA = id(25); // contacto interno: la cuenta de Ana
const M1 = id(31), M2 = id(32), P1 = id(33), P2 = id(34), P3 = id(35), R1 = id(36), R2 = id(37); // vínculos
const W1 = id(41), W2 = id(42), W3 = id(43), W4 = id(44), W5 = id(45); // relojes
const A_MARIA = id(51), A_PEDRO = id(52), A_ROSA_VIEJA = id(53), A_ROSA = id(54); // asignaciones
const S_MARIA = id(61), S_PEDRO = id(62), S_ROSA_VIEJA = id(63), S_ROSA = id(64), S_MARIA_2 = id(65); // lotes
const CAIDA_MARIA = id(71), LEVE_PEDRO = id(72), MEDIA_ROSA = id(73), GRAVE_ROSA = id(74);
const CANCELADA = id(75), SILENCIO = id(76), SIMULACRO = id(77), SILENCIO_2 = id(78), GRAVE_ROSA_2 = id(79); // alertas
const T1 = '2026-10-01 12:00Z', T2 = '2026-10-10 12:00Z';
const MADRUGADA = '2026-10-15 03:00-03';

const alertaReloj = (idA, asig, tipo, sev, extraCols = '', extraVals = '') =>
  `INSERT INTO "ALERTA"(id_alerta, event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad, fecha_hora_deteccion, fecha_hora_recepcion${extraCols})
   VALUES ('${idA}', gen_random_uuid(), '${asig}', 'h1', 'RELOJ', '${tipo}', '${sev}', '${MADRUGADA}', now()${extraVals})`;
const alertaServidor = (idA, asig, tipo, extraCols = '', extraVals = '') =>
  `INSERT INTO "ALERTA"(id_alerta, event_id, id_asignacion, origen, tipo_evento, severidad, fecha_hora_recepcion${extraCols})
   VALUES ('${idA}', gen_random_uuid(), '${asig}', 'SERVIDOR', '${tipo}', 'LEVE', now()${extraVals})`;
const aviso = (alerta, vinculo, ronda = 1, extraCols = '', extraVals = '', canal = 'WHATSAPP') =>
  `INSERT INTO "REGISTRO_INTENTO"(id_intento, event_id, id_alerta, id_contacto_paciente, canal, ronda, fecha_hora_envio${extraCols})
   VALUES (gen_random_uuid(), gen_random_uuid(), '${alerta}', '${vinculo}', '${canal}', ${ronda}, now()${extraVals})`;
// Correo del servidor a una cuenta del equipo (contacto interno), sin ronda
const avisoInterno = (alerta, contacto, extraCols = '', extraVals = '') =>
  `INSERT INTO "REGISTRO_INTENTO"(id_intento, event_id, id_alerta, id_contacto_interno, canal${extraCols})
   VALUES (gen_random_uuid(), gen_random_uuid(), '${alerta}', '${contacto}', 'CORREO'${extraVals})`;
const asumir = (alerta, usuario, hora, condicion = `"ATENCION_ALERTA".fecha_hora_toma IS NULL AND "ATENCION_ALERTA".fecha_hora_cierre IS NULL`) =>
  `INSERT INTO "ATENCION_ALERTA"(id_alerta, id_usuario_responsable, fecha_hora_toma) VALUES ('${alerta}', '${usuario}', '${hora}')
   ON CONFLICT (id_alerta) DO UPDATE SET id_usuario_responsable = EXCLUDED.id_usuario_responsable, fecha_hora_toma = EXCLUDED.fecha_hora_toma
   WHERE ${condicion}`;
const estado = (caso, alerta, contacto, atencion) => vale(caso,
  `SELECT estado_contacto || ' / ' || estado_atencion n FROM "ALERTA_ESTADO" WHERE id_alerta = '${alerta}'`, `${contacto} / ${atencion}`);
const pendiente = (caso, asig, hashBackend, esperado) => vale(caso,
  `SELECT (SELECT hash_configuracion FROM "SINCRONIZACION" WHERE id_asignacion = '${asig}' ORDER BY fecha_hora_recepcion DESC LIMIT 1)
   IS DISTINCT FROM '${hashBackend}' n`, esperado);
const usuario = (idU, rol, nombre, correo, rutHash, extraCols = '', extraVals = '') =>
  `INSERT INTO "USUARIO"(id_usuario, codigo_rol, rut, rut_hash, nombre_profesional, correo${extraCols})
   VALUES ('${idU}', '${rol}', 'rut-cifrado', '${rutHash}', '${nombre}', '${correo}'${extraVals})`;

// =====================================================================
nuevoGrupo('A. Relaciones uno a uno (en una base aparte)');
await vale('La clave foránea de la ficha está en FICHA_PACIENTE (apunta a PACIENTE)', `SELECT count(*) n FROM information_schema.table_constraints
  WHERE constraint_type = 'FOREIGN KEY' AND table_name = 'FICHA_PACIENTE'`, 1);
await vale('La clave foránea de la atención está en ATENCION_ALERTA (apunta a ALERTA)', `SELECT count(*) n FROM information_schema.table_constraints
  WHERE constraint_type = 'FOREIGN KEY' AND table_name = 'ATENCION_ALERTA' AND constraint_name LIKE '%id_alerta%'`, 1);
await acepta('Alta de un paciente sin ficha todavía', `INSERT INTO "PACIENTE"(id_paciente, rut, rut_hash, fecha_ingreso) VALUES ('${MARIA}', 'c', 'rut-m', '2026-10-01')`);
await db.exec(`INSERT INTO "PACIENTE"(id_paciente, rut, rut_hash, fecha_ingreso) VALUES ('${PEDRO}', 'c', 'rut-p', '2026-10-01');
  INSERT INTO "FICHA_PACIENTE"(id_paciente) VALUES ('${PEDRO}');
  INSERT INTO "SMARTWATCH"(id_dispositivo, identificador_reloj, estado) VALUES ('${W2}', 'W-002', 'OPERATIVO');
  INSERT INTO "ASIGNACION_RELOJ"(id_asignacion, id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj) VALUES ('${A_PEDRO}', '${W2}', '${PEDRO}', '${T1}', 'tok-2')`);
await acepta('Llega una alerta que nadie ha asumido (debe quedar SIN_ASIGNAR)', alertaReloj(LEVE_PEDRO, A_PEDRO, 'FC', 'LEVE'));
await acepta('Se borra la ficha de Pedro (por ejemplo, para rehacerla)', `DELETE FROM "FICHA_PACIENTE" WHERE id_paciente = '${PEDRO}'`);
await vale('Borrar la ficha no borra al paciente', `SELECT count(*) n FROM "PACIENTE" WHERE id_paciente = '${PEDRO}'`, 1);

// =====================================================================
db = dbPrincipal;

nuevoGrupo('1. Estructura de la base');
await vale('Cantidad de tablas', `SELECT count(*) n FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'`, 30);
await vale('Tablas sueltas (sin ninguna relación)', `SELECT count(*) n FROM information_schema.tables t
  WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints c WHERE c.constraint_type = 'FOREIGN KEY' AND c.table_name = t.table_name)
  AND NOT EXISTS (SELECT 1 FROM information_schema.constraint_column_usage u
                  JOIN information_schema.table_constraints c ON c.constraint_name = u.constraint_name AND c.constraint_type = 'FOREIGN KEY'
                  WHERE u.table_name = t.table_name)`, 0);
await vale('Cantidad de relaciones (claves foráneas)', `SELECT count(*) n FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY'`, 35);
await vale('Cantidad de tipos (Enum)', `SELECT count(*) n FROM pg_type t JOIN pg_namespace s ON s.oid = t.typnamespace WHERE s.nspname = 'public' AND t.typtype = 'e'`, 21);
await vale('Vistas de datos calculados', `SELECT count(*) n FROM information_schema.views WHERE table_schema = 'public'`, 1);
await vale('Ninguna columna jsonb en la configuración clínica', `SELECT count(*) n FROM information_schema.columns
  WHERE table_name IN ('CONFIGURACION', 'UMBRAL') AND data_type = 'jsonb'`, 0);

// =====================================================================
nuevoGrupo('2. Roles, permisos y profesionales');
await acepta('Catálogo de roles (ADMINISTRADOR es de sistema)', `INSERT INTO "ROL"(codigo, nombre, es_sistema) VALUES
  ('ADMINISTRADOR', 'Administrador', true), ('COORDINADOR', 'Coordinador', false), ('PERSONAL', 'Personal', false)`);
await acepta('Permisos por rol, módulo y acción', `INSERT INTO "PERMISO_ROL" VALUES
  ('ADMINISTRADOR', 'USUARIOS', 'CREAR'), ('ADMINISTRADOR', 'ADMINISTRACION', 'EDITAR'),
  ('COORDINADOR', 'ALERTAS', 'EDITAR'), ('COORDINADOR', 'USUARIOS', 'CREAR'), ('COORDINADOR', 'REPORTES', 'EXPORTAR'),
  ('PERSONAL', 'ALERTAS', 'VER'), ('PERSONAL', 'ALERTAS', 'EDITAR')`);
await acepta('Alta de tres profesionales activos', [
  usuario(ANA, 'PERSONAL', 'Ana', 'ana@muni.cl', 'rut-ana', ', hash_contrasena, activo', `, 'h', true`),
  usuario(LUIS, 'COORDINADOR', 'Luis', 'luis@muni.cl', 'rut-luis', ', hash_contrasena, activo', `, 'h', true`),
  usuario(ADMIN, 'ADMINISTRADOR', 'Admin', 'admin@muni.cl', 'rut-admin', ', hash_contrasena, activo', `, 'h', true`)].join(';'));
await vale('Ana puede editar alertas (permiso de su rol)', `SELECT count(*) n FROM "USUARIO" u JOIN "PERMISO_ROL" p ON p.codigo_rol = u.codigo_rol
  WHERE u.id_usuario = '${ANA}' AND p.modulo = 'ALERTAS' AND p.accion = 'EDITAR'`, 1);
await vale('Ana no puede ver reportes', `SELECT count(*) n FROM "USUARIO" u JOIN "PERMISO_ROL" p ON p.codigo_rol = u.codigo_rol
  WHERE u.id_usuario = '${ANA}' AND p.modulo = 'REPORTES' AND p.accion = 'VER'`, 0);
await rechaza('Permiso repetido', `INSERT INTO "PERMISO_ROL" VALUES ('PERSONAL', 'ALERTAS', 'VER')`, 'unico');
await rechaza('Módulo que no existe (FINANZAS)', `INSERT INTO "PERMISO_ROL" VALUES ('PERSONAL', 'FINANZAS', 'VER')`, 'enum');
await rechaza('Permiso de un rol que no existe', `INSERT INTO "PERMISO_ROL" VALUES ('SUPERUSUARIO', 'ALERTAS', 'VER')`, 'foranea');
await rechaza('Profesional con un rol que no existe', usuario(gen(), 'SUPERUSUARIO', 'X', 'x@muni.cl', 'rut-x'), 'foranea');
await rechaza('Borrar un rol que tiene profesionales', `DELETE FROM "ROL" WHERE codigo = 'COORDINADOR'`, 'foranea');
await acepta('Borrar un rol sin profesionales borra sus permisos (se deshace)', `BEGIN; INSERT INTO "ROL"(codigo, nombre) VALUES ('TEMPORAL', 'Temporal');
  INSERT INTO "PERMISO_ROL" VALUES ('TEMPORAL', 'ALERTAS', 'VER'); DELETE FROM "ROL" WHERE codigo = 'TEMPORAL'; ROLLBACK`);
await backend('Quitar todos los permisos del ADMINISTRADOR (rol de sistema)', `DELETE FROM "PERMISO_ROL" WHERE codigo_rol = 'ADMINISTRADOR'`,
  'Nota de PERMISO_ROL: "los permisos de un rol con es_sistema no se agregan, quitan ni borran".');
await acepta('Cuenta nueva creada por el administrador: inactiva, sin contraseña, con enlace de activación', usuario(NUEVA, 'PERSONAL','Nueva', 'nueva@muni.cl', 'rut-nueva')
  + `; INSERT INTO "TOKEN_ACCION"(id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion) VALUES (gen_random_uuid(), '${NUEVA}', 'ACTIVACION', 'act-1', now(), now() + interval '2 days')`);
await rechaza('Activar la cuenta sin contraseña', `UPDATE "USUARIO" SET activo = true WHERE id_usuario = '${NUEVA}'`, 'check');
await acepta('La usuaria define su contraseña: se activa y el enlace queda usado', `UPDATE "USUARIO" SET hash_contrasena = 'h', activo = true WHERE id_usuario = '${NUEVA}';
  UPDATE "TOKEN_ACCION" SET fecha_uso = now() WHERE hash_token = 'act-1'`);
await rechaza('Dos profesionales con el mismo correo', usuario(gen(), 'PERSONAL','Otra', 'ana@muni.cl', 'rut-otra'), 'unico');
await rechaza('El mismo correo con otras mayúsculas (ANA@muni.cl): se guarda en minúsculas', usuario(gen(), 'PERSONAL','Otra', 'ANA@muni.cl', 'rut-otra'), 'check');
await backend('Desactivar a una usuaria con un enlace de activación pendiente', `UPDATE "USUARIO" SET activo = false WHERE id_usuario = '${LUIS}';
  INSERT INTO "TOKEN_ACCION"(id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion) VALUES (gen_random_uuid(), '${LUIS}', 'ACTIVACION', 'act-luis', now(), now() + interval '2 days')`,
  'Nota de USUARIO: "al desactivar, marca como usados sus TOKEN_ACCION pendientes y revoca sus TOKEN_SESION".');
await rechaza('Dos profesionales con el mismo RUN', usuario(gen(), 'PERSONAL','Otra', 'otra@muni.cl', 'rut-ana'), 'unico');
await acepta('Coordinadora con teléfono y opt-in de WhatsApp (se deshace)', 'BEGIN; '
  + usuario(gen(), 'COORDINADOR', 'Rosa', 'rosa@muni.cl', 'rut-rosa', ', telefono, fecha_opt_in', ", 'tel-cifrado', now()") + '; ROLLBACK');
await rechaza('Opt-in de WhatsApp sin teléfono', usuario(gen(), 'COORDINADOR', 'Rosa', 'rosa@muni.cl', 'rut-rosa', ', fecha_opt_in', ', now()'), 'check');
await rechaza('Revocación del opt-in sin haberlo aceptado', usuario(gen(), 'PERSONAL', 'Otra', 'otra@muni.cl', 'rut-otra',
  ', telefono, fecha_revocacion_opt_in', ", 'tel-cifrado', now()"), 'check');
await backend('Coordinadora sin teléfono (no recibiría los avisos de la oficina)', usuario(gen(), 'COORDINADOR', 'Sin', 'sin@muni.cl', 'rut-sin'),
  'Nota de USUARIO: el teléfono es obligatorio para el rol COORDINADOR (lo valida el backend al crear la cuenta).');
await rechaza('Profesional sin RUN', `INSERT INTO "USUARIO"(id_usuario, codigo_rol, rut_hash, nombre_profesional, correo) VALUES (gen_random_uuid(), 'PERSONAL','r', 'X', 'x@muni.cl')`, 'nulo');
await rechaza('Enlace con un tipo que no existe', `INSERT INTO "TOKEN_ACCION"(id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion) VALUES (gen_random_uuid(), '${ANA}', 'INVITACION', 'x', now(), now() + interval '30 minutes')`, 'enum');
await rechaza('Enlace repetido', `INSERT INTO "TOKEN_ACCION"(id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion) VALUES (gen_random_uuid(), '${ANA}', 'RECUPERACION', 'act-1', now(), now() + interval '30 minutes')`, 'unico');
await rechaza('Token de sesión que vence antes de emitirse', `INSERT INTO "TOKEN_SESION"(id_token, id_usuario, hash_token, fecha_emision, fecha_expiracion) VALUES (gen_random_uuid(), '${ANA}', 'ses-x', now(), now() - interval '1 day')`, 'check');
await rechaza('Enlace que vence al mismo tiempo que se emite', `INSERT INTO "TOKEN_ACCION"(id_token, id_usuario, tipo, hash_token, fecha_emision, fecha_expiracion) VALUES (gen_random_uuid(), '${ANA}', 'RECUPERACION', 'rec-x', now(), now())`, 'check');
await acepta('Sesión y códigos de respaldo de Ana', `INSERT INTO "TOKEN_SESION"(id_token, id_usuario, hash_token, fecha_emision, fecha_expiracion) VALUES (gen_random_uuid(), '${ANA}', 'ses-1', now(), now() + interval '7 days');
  INSERT INTO "CODIGO_RESPALDO"(id_codigo, id_usuario, hash_codigo) VALUES (gen_random_uuid(), '${ANA}', 'cod-1'), (gen_random_uuid(), '${ANA}', 'cod-2')`);
await rechaza('Código de respaldo repetido para la misma profesional', `INSERT INTO "CODIGO_RESPALDO"(id_codigo, id_usuario, hash_codigo) VALUES (gen_random_uuid(), '${ANA}', 'cod-1')`, 'unico');
await rechaza('Token de sesión repetido', `INSERT INTO "TOKEN_SESION"(id_token, id_usuario, hash_token, fecha_emision, fecha_expiracion) VALUES (gen_random_uuid(), '${LUIS}', 'ses-1', now(), now() + interval '7 days')`, 'unico');

// =====================================================================
nuevoGrupo('3. Inicio de sesión calculado desde la auditoría');
await acepta('Tres intentos fallidos de Ana en 10 minutos', `INSERT INTO "REGISTRO_AUDITORIA"(id_auditoria, id_usuario, accion, fecha_hora, ip) VALUES
  (gen_random_uuid(), '${ANA}', 'LOGIN', now() - interval '1 day', '10.0.0.1'),
  (gen_random_uuid(), '${ANA}', 'LOGIN_FALLIDO', now() - interval '10 minutes', '10.0.0.1'),
  (gen_random_uuid(), '${ANA}', 'LOGIN_FALLIDO', now() - interval '8 minutes', '10.0.0.1'),
  (gen_random_uuid(), '${ANA}', 'LOGIN_FALLIDO', now() - interval '5 minutes', '10.0.0.1')`);
const bloqueada = `SELECT (count(*) >= 3)::text n FROM "REGISTRO_AUDITORIA" f
  WHERE f.id_usuario = '${ANA}' AND f.accion = 'LOGIN_FALLIDO' AND f.fecha_hora > now() - interval '15 minutes'
  AND NOT EXISTS (SELECT 1 FROM "REGISTRO_AUDITORIA" l WHERE l.id_usuario = f.id_usuario AND l.accion = 'LOGIN' AND l.fecha_hora > f.fecha_hora)`;
await vale('Ana está bloqueada (3 fallidos en 15 min, valores del yml)', bloqueada, 'true');
await acepta('El administrador la desbloquea y Ana entra', `INSERT INTO "REGISTRO_AUDITORIA"(id_auditoria, id_usuario, accion, fecha_hora) VALUES (gen_random_uuid(), '${ANA}', 'LOGIN', now() - interval '1 minute')`);
await vale('Ya no está bloqueada', bloqueada, 'false');
await vale('Último acceso de Ana (hace 1 minuto)', `SELECT (max(fecha_hora) > now() - interval '2 minutes')::text n FROM "REGISTRO_AUDITORIA" WHERE id_usuario = '${ANA}' AND accion = 'LOGIN'`, 'true');
await acepta('Intento fallido con un RUN que no existe (sin usuario)', `INSERT INTO "REGISTRO_AUDITORIA"(id_auditoria, accion, fecha_hora) VALUES (gen_random_uuid(), 'LOGIN_FALLIDO', now())`);
await rechaza('Auditoría con entidad pero sin su id', `INSERT INTO "REGISTRO_AUDITORIA"(id_auditoria, id_usuario, accion, entidad, fecha_hora) VALUES (gen_random_uuid(), '${ANA}', 'LEER', 'PACIENTE', now())`, 'check');
await rechaza('Acción de auditoría que no existe', `INSERT INTO "REGISTRO_AUDITORIA"(id_auditoria, accion, fecha_hora) VALUES (gen_random_uuid(), 'IMPRIMIR', now())`, 'enum');

// =====================================================================
nuevoGrupo('4. Pacientes y datos de salud');
await acepta('Alta de tres pacientes con consentimiento firmado', `INSERT INTO "PACIENTE"(id_paciente, rut, rut_hash, nombres, alias, fecha_nacimiento, sexo, nivel_dependencia, fecha_consentimiento, fecha_ingreso) VALUES
  ('${MARIA}', 'c', 'rut-m', 'María', 'María', '1940-03-10', 'FEMENINO', 'SEVERA', '2026-09-30', '2026-10-01'),
  ('${PEDRO}', 'c', 'rut-p', 'Pedro', 'Pedro', '1945-11-02', 'MASCULINO', 'MODERADA', '2026-09-30', '2026-10-01'),
  ('${ROSA}', 'c', 'rut-r', 'Rosa', 'Rosa', '1938-01-20', 'FEMENINO', null, '2026-09-30', '2026-10-01')`);
await vale('Pacientes por evaluar (dependencia vacía)', `SELECT count(*) n FROM "PACIENTE" WHERE nivel_dependencia IS NULL`, 1);
await vale('La fecha de nacimiento (cifrada) se guarda como texto; la edad la calcula el backend', `SELECT data_type n FROM information_schema.columns
  WHERE table_name = 'PACIENTE' AND column_name = 'fecha_nacimiento'`, 'character varying');
await acepta('Ficha de María (uno a uno), dos enfermedades y dos situaciones', `INSERT INTO "FICHA_PACIENTE"(id_paciente, registro_discapacidad, notas) VALUES ('${MARIA}', true, 'Vive sola');
  INSERT INTO "CONDICION_PACIENTE"(id_condicion, id_paciente, tipo, descripcion) VALUES
  (gen_random_uuid(), '${MARIA}', 'ENFERMEDAD', 'EPOC'), (gen_random_uuid(), '${MARIA}', 'ENFERMEDAD', 'Hipertensión'),
  (gen_random_uuid(), '${MARIA}', 'SITUACION', 'Movilidad reducida'), (gen_random_uuid(), '${MARIA}', 'SITUACION', 'Discapacidad visual')`);
await vale('Ficha de María: enfermedades / situaciones', `SELECT count(*) FILTER (WHERE tipo = 'ENFERMEDAD') || ' / ' || count(*) FILTER (WHERE tipo = 'SITUACION') n
  FROM "CONDICION_PACIENTE" WHERE id_paciente = '${MARIA}'`, '2 / 2');
await rechaza('Segunda ficha para el mismo paciente', `INSERT INTO "FICHA_PACIENTE"(id_paciente) VALUES ('${MARIA}')`, 'unico');
await rechaza('Ficha de un paciente que no existe', `INSERT INTO "FICHA_PACIENTE"(id_paciente) VALUES (gen_random_uuid())`, 'foranea');
await rechaza('Condición sin descripción', `INSERT INTO "CONDICION_PACIENTE"(id_condicion, id_paciente, tipo) VALUES (gen_random_uuid(), '${MARIA}', 'ENFERMEDAD')`, 'nulo');
await rechaza('Condición sin indicar si es enfermedad o situación', `INSERT INTO "CONDICION_PACIENTE"(id_condicion, id_paciente, descripcion) VALUES (gen_random_uuid(), '${MARIA}', 'Diabetes')`, 'nulo');
await rechaza('Tipo de condición que no existe (ALERGIA)', `INSERT INTO "CONDICION_PACIENTE"(id_condicion, id_paciente, tipo, descripcion) VALUES (gen_random_uuid(), '${MARIA}', 'ALERGIA', 'Penicilina')`, 'enum');
await acepta('Ana sube el consentimiento de María', `INSERT INTO "DOCUMENTO"(id_documento, id_paciente, tipo, nombre_archivo, ruta, hash_sha256, fecha_subida, id_usuario)
  VALUES (gen_random_uuid(), '${MARIA}', 'CONSENTIMIENTO', 'consentimiento.pdf', '/docs/m/1.enc', 'sha-1', now(), '${ANA}')`);
await rechaza('Dos documentos en la misma ruta', `INSERT INTO "DOCUMENTO"(id_documento, id_paciente, tipo, nombre_archivo, ruta, hash_sha256, fecha_subida, id_usuario)
  VALUES (gen_random_uuid(), '${PEDRO}', 'OTRO', 'x.pdf', '/docs/m/1.enc', 'sha-2', now(), '${ANA}')`, 'unico');
await rechaza('Documento subido por un usuario que no existe', `INSERT INTO "DOCUMENTO"(id_documento, id_paciente, tipo, nombre_archivo, ruta, hash_sha256, fecha_subida, id_usuario)
  VALUES (gen_random_uuid(), '${PEDRO}', 'OTRO', 'x.pdf', '/docs/p/1.enc', 'sha-2', now(), gen_random_uuid())`, 'foranea');
await rechaza('Tipo de documento que no existe', `INSERT INTO "DOCUMENTO"(id_documento, id_paciente, tipo, nombre_archivo, ruta, hash_sha256, fecha_subida, id_usuario)
  VALUES (gen_random_uuid(), '${PEDRO}', 'RECETA', 'x.pdf', '/docs/p/1.enc', 'sha-2', now(), '${ANA}')`, 'enum');
await rechaza('Paciente con RUN repetido', `INSERT INTO "PACIENTE"(id_paciente, rut, rut_hash, fecha_ingreso) VALUES (gen_random_uuid(), 'c', 'rut-m', '2026-10-01')`, 'unico');
await rechaza('Paciente sin fecha de ingreso', `INSERT INTO "PACIENTE"(id_paciente, rut, rut_hash) VALUES (gen_random_uuid(), 'c', 'rut-z')`, 'nulo');
await rechaza('Baja anterior al ingreso', `UPDATE "PACIENTE" SET fecha_baja = '2026-01-01' WHERE id_paciente = '${MARIA}'`, 'check');
await rechaza('Sexo que no existe en el catálogo', `INSERT INTO "PACIENTE"(id_paciente, rut, rut_hash, sexo, fecha_ingreso) VALUES (gen_random_uuid(), 'c', 'rut-z', 'OTRO', '2026-10-01')`, 'enum');
await rechaza('Paciente con rut_hash pero sin el RUN cifrado', `INSERT INTO "PACIENTE"(id_paciente, rut_hash, fecha_ingreso) VALUES (gen_random_uuid(), 'rut-z', '2026-10-01')`, 'nulo');

// =====================================================================
nuevoGrupo('5. Contactos (compartidos, exclusivos y horarios)');
await acepta('Catálogo de tipos de contacto', `INSERT INTO "TIPO_CONTACTO"(codigo, nombre) VALUES
  ('FAMILIA', 'Familia'), ('VECINO', 'Vecino'), ('SEGURIDAD_CIUDADANA', 'Seguridad Ciudadana'), ('OTRO', 'Otro')`);
await acepta('Cuatro personas de contacto (una solo con correo)', `INSERT INTO "CONTACTO"(id_contacto, nombre, whatsapp, whatsapp_hash, fecha_opt_in, correo, correo_hash) VALUES
  ('${HIJA}', 'Hija de María', '+56911', 'w-hija', now(), null, null),
  ('${SEG_CIUDADANA}', 'Seguridad Ciudadana', '+56922', 'w-sc', now(), 'sc@muni.cl', 'm-sc'),
  ('${VECINO}', 'Vecino Juan', null, null, null, 'juan@mail.cl', 'm-juan'),
  ('${CUIDADORA}', 'Cuidadora de Pedro', '+56944', 'w-cuid', now(), null, null)`);
await acepta('Listas de contactos: Seguridad Ciudadana en los tres pacientes; la cuidadora de Pedro de noche (22:00–07:00)', `INSERT INTO "CONTACTO_PACIENTE"
  (id_contacto_paciente, id_paciente, id_contacto, codigo_tipo_contacto, parentesco, es_cuidador_principal, orden_prioridad, hora_inicio, hora_fin) VALUES
  ('${M1}', '${MARIA}', '${HIJA}', 'FAMILIA', 'hija', true, 1, null, null),
  ('${M2}', '${MARIA}', '${SEG_CIUDADANA}', 'SEGURIDAD_CIUDADANA', null, false, 2, null, null),
  ('${P1}', '${PEDRO}', '${CUIDADORA}', 'OTRO', null, true, 1, '22:00', '07:00'),
  ('${P2}', '${PEDRO}', '${SEG_CIUDADANA}', 'SEGURIDAD_CIUDADANA', null, false, 2, null, null),
  ('${P3}', '${PEDRO}', '${VECINO}', 'VECINO', null, false, 3, null, null),
  ('${R1}', '${ROSA}', '${SEG_CIUDADANA}', 'SEGURIDAD_CIUDADANA', null, false, 1, null, null),
  ('${R2}', '${ROSA}', '${VECINO}', 'VECINO', null, false, 2, null, null)`);
await vale('Seguridad Ciudadana está guardada una sola vez', `SELECT count(*) n FROM "CONTACTO" WHERE whatsapp_hash = 'w-sc'`, 1);
await vale('Seguridad Ciudadana es contacto de tres pacientes (compartido)', `SELECT count(*) n FROM "CONTACTO_PACIENTE" WHERE id_contacto = '${SEG_CIUDADANA}'`, 3);
const disponible = (vinculo, hora) => `SELECT (hora_inicio IS NULL OR (hora_inicio < hora_fin AND '${hora}'::time >= hora_inicio AND '${hora}'::time < hora_fin)
  OR (hora_inicio > hora_fin AND ('${hora}'::time >= hora_inicio OR '${hora}'::time < hora_fin)))::text n FROM "CONTACTO_PACIENTE" WHERE id_contacto_paciente = '${vinculo}'`;
await vale('La cuidadora de Pedro está disponible a las 03:00 (horario que cruza la medianoche)', disponible(P1, '03:00'), 'true');
await vale('La cuidadora de Pedro no está disponible a las 15:00', disponible(P1, '15:00'), 'false');
await filas('Meta informa la baja del opt-in de un número: se encuentra a la persona por su hash', `UPDATE "CONTACTO" SET fecha_revocacion_opt_in = now() WHERE whatsapp_hash = 'w-cuid'`, 1);
await rechaza('Misma persona registrada dos veces (mismo WhatsApp)', `INSERT INTO "CONTACTO"(id_contacto, nombre, whatsapp, whatsapp_hash) VALUES (gen_random_uuid(), 'Otra', '+56922', 'w-sc')`, 'unico');
await rechaza('Misma persona registrada dos veces (mismo correo)', `INSERT INTO "CONTACTO"(id_contacto, nombre, correo, correo_hash) VALUES (gen_random_uuid(), 'Otro', 'juan@mail.cl', 'm-juan')`, 'unico');
await rechaza('Persona sin WhatsApp ni correo', `INSERT INTO "CONTACTO"(id_contacto, nombre) VALUES (gen_random_uuid(), 'X')`, 'check');
await rechaza('WhatsApp sin su hash', `INSERT INTO "CONTACTO"(id_contacto, nombre, whatsapp) VALUES (gen_random_uuid(), 'X', '+56999')`, 'check');
await rechaza('Correo sin su hash', `INSERT INTO "CONTACTO"(id_contacto, nombre, correo) VALUES (gen_random_uuid(), 'X', 'x@x.cl')`, 'check');
await rechaza('Baja del opt-in sin opt-in previo', `INSERT INTO "CONTACTO"(id_contacto, nombre, correo, correo_hash, fecha_revocacion_opt_in) VALUES (gen_random_uuid(), 'X', 'x@x.cl', 'm-x', now())`, 'check');
const vinculo = (pac, cont, tipo, prio, extraCols = '', extraVals = '') =>
  `INSERT INTO "CONTACTO_PACIENTE"(id_contacto_paciente, id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad${extraCols})
   VALUES (gen_random_uuid(), '${pac}', '${cont}', '${tipo}', ${prio}${extraVals})`;
await rechaza('La misma persona dos veces en la lista de un paciente', vinculo(MARIA, HIJA, 'FAMILIA', 5), 'unico');
await rechaza('Dos contactos activos con la misma prioridad', vinculo(MARIA, VECINO, 'VECINO', 1), 'unico');
await acepta('Repetir prioridad con un contacto inactivo (se deshace)', `BEGIN; ${vinculo(MARIA, VECINO, 'VECINO', 1, ', activo', ', false')}; ROLLBACK`);
await rechaza('Dos cuidadores principales activos para el mismo paciente', vinculo(MARIA, VECINO, 'VECINO', 3, ', es_cuidador_principal', ', true'), 'unico');
await rechaza('Prioridad 0', vinculo(MARIA, VECINO, 'VECINO', 0), 'check');
await rechaza('Horario con inicio pero sin fin', vinculo(MARIA, VECINO, 'VECINO', 3, ', hora_inicio', `, '08:00'`), 'check');
await rechaza('Horario que empieza y termina a la misma hora', vinculo(MARIA, VECINO, 'VECINO', 3, ', hora_inicio, hora_fin', `, '08:00', '08:00'`), 'check');
await rechaza('Parentesco en un contacto que no es FAMILIA', vinculo(MARIA, VECINO, 'VECINO', 3, ', parentesco', `, 'sobrino'`), 'check');
await rechaza('Tipo de contacto que no existe (AMBULANCIA)', vinculo(MARIA, VECINO, 'AMBULANCIA', 3), 'foranea');

// =====================================================================
nuevoGrupo('6. Configuración clínica y umbrales (historiales separados)');
await acepta('María: configuración inicial y un cambio posterior (activa el GPS)', `INSERT INTO "CONFIGURACION"(id_paciente, vigente_desde) VALUES ('${MARIA}', '${T1}');
  INSERT INTO "CONFIGURACION"(id_paciente, vigente_desde, gps_activo, minutos_espera, motivo_cambio) VALUES ('${MARIA}', '${T2}', true, 5, 'Sale sola a caminar')`);
await acepta('Pedro con valores por defecto; Rosa en modo conservador', `INSERT INTO "CONFIGURACION"(id_paciente, vigente_desde) VALUES ('${PEDRO}', '${T1}');
  INSERT INTO "CONFIGURACION"(id_paciente, vigente_desde, modo_conservador) VALUES ('${ROSA}', '${T1}', true)`);
await vale('Módulos por defecto (caídas, FC, SpO2, inactividad, tendencia y botón sí; GPS y zona no)', `SELECT concat_ws(',', caidas_activa::text, fc_activa::text, spo2_activa::text, inactividad_activa::text, tendencia_activa::text, gps_activo::text, zona_segura_activa::text, boton_ayuda_activo::text) n
  FROM "CONFIGURACION" WHERE id_paciente = '${PEDRO}'`, 'true,true,true,true,true,false,false,true');
await rechaza('Zona segura activa sin GPS', `INSERT INTO "CONFIGURACION"(id_paciente, vigente_desde, zona_segura_activa) VALUES ('${PEDRO}', now(), true)`, 'check');
await acepta('Zona segura de Pedro: 200 m, media por defecto, coordenadas cifradas como texto', `INSERT INTO "ZONA_SEGURA"(id_paciente, vigente_desde, latitud, longitud, radio_metros)
  VALUES ('${PEDRO}', '${T1}', 'c-lat', 'c-lon', 200)`);
await vale('La zona sin severidad elegida es media', `SELECT severidad n FROM "ZONA_SEGURA" WHERE id_paciente = '${PEDRO}'`, 'MEDIA');
await acepta('Pedro activa la zona con el GPS (se deshace)', `BEGIN; INSERT INTO "CONFIGURACION"(id_paciente, vigente_desde, gps_activo, zona_segura_activa)
  VALUES ('${PEDRO}', now(), true, true); ROLLBACK`);
await rechaza('Zona segura con radio cero', `INSERT INTO "ZONA_SEGURA"(id_paciente, vigente_desde, latitud, longitud, radio_metros) VALUES ('${PEDRO}', now(), 'c-lat', 'c-lon', 0)`, 'check');
await rechaza('Zona segura con una severidad que no existe', `INSERT INTO "ZONA_SEGURA"(id_paciente, vigente_desde, latitud, longitud, radio_metros, severidad)
  VALUES ('${PEDRO}', now(), 'c-lat', 'c-lon', 200, 'CRITICA')`, 'enum');
await rechaza('Dos zonas de Pedro con la misma fecha', `INSERT INTO "ZONA_SEGURA"(id_paciente, vigente_desde, latitud, longitud, radio_metros) VALUES ('${PEDRO}', '${T1}', 'c-lat', 'c-lon', 300)`, 'unico');
await vale('Configuración vigente de María (la más reciente): GPS activo', `SELECT gps_activo n FROM "CONFIGURACION" WHERE id_paciente = '${MARIA}' ORDER BY vigente_desde DESC LIMIT 1`, true);
await acepta('Umbrales de María: FC (dos versiones) y SpO2 sin límite alto', `INSERT INTO "UMBRAL" VALUES
  ('${MARIA}', 'FC', '${T1}', 40, 50, 110, 130), ('${MARIA}', 'FC', '${T2}', 45, 55, 110, 130), ('${MARIA}', 'SPO2', '${T1}', 85, 90, null, null)`);
await vale('Historiales independientes: 2 configuraciones y 3 umbrales de María', `SELECT (SELECT count(*) FROM "CONFIGURACION" WHERE id_paciente = '${MARIA}') || ' / ' || (SELECT count(*) FROM "UMBRAL" WHERE id_paciente = '${MARIA}') n`, '2 / 3');
await vale('Umbral FC vigente de María: normal desde 55', `SELECT normal_min n FROM "UMBRAL" WHERE id_paciente = '${MARIA}' AND variable = 'FC' ORDER BY vigente_desde DESC LIMIT 1`, 55);
await vale('Umbral FC que regía el 05-10 (versión del 01-10): normal desde 50', `SELECT normal_min n FROM "UMBRAL" WHERE id_paciente = '${MARIA}' AND variable = 'FC'
  AND vigente_desde <= '2026-10-05' ORDER BY vigente_desde DESC LIMIT 1`, 50);
await acepta('María vuelve al umbral base de SpO2 sin borrar historial (fila con las cuatro columnas vacías)', `INSERT INTO "UMBRAL"(id_paciente, variable, vigente_desde) VALUES ('${MARIA}', 'SPO2', '${T2}')`);
await vale('Para SpO2 rige el umbral base', `SELECT (grave_bajo IS NULL AND normal_min IS NULL AND normal_max IS NULL AND grave_alto IS NULL)::text n
  FROM "UMBRAL" WHERE id_paciente = '${MARIA}' AND variable = 'SPO2' ORDER BY vigente_desde DESC LIMIT 1`, 'true');
await vale('Pedro no tiene umbrales propios (rige el base)', `SELECT count(*) n FROM "UMBRAL" WHERE id_paciente = '${PEDRO}'`, 0);
await rechaza('Dos configuraciones con la misma fecha', `INSERT INTO "CONFIGURACION"(id_paciente, vigente_desde) VALUES ('${MARIA}', '${T2}')`, 'unico');
await rechaza('Minutos de espera en cero', `INSERT INTO "CONFIGURACION"(id_paciente, vigente_desde, minutos_espera) VALUES ('${MARIA}', now(), 0)`, 'check');
await rechaza('Dos umbrales de la misma variable con la misma fecha', `INSERT INTO "UMBRAL"(id_paciente, variable, vigente_desde) VALUES ('${MARIA}', 'FC', '${T2}')`, 'unico');
await rechaza('Normal mínimo mayor que el normal máximo', `INSERT INTO "UMBRAL" VALUES ('${PEDRO}', 'FC', now(), 40, 110, 50, 130)`, 'check');
await rechaza('Grave bajo por encima del normal mínimo', `INSERT INTO "UMBRAL" VALUES ('${PEDRO}', 'FC', now(), 60, 50, 110, 130)`, 'check');
await rechaza('Grave alto por debajo del normal máximo', `INSERT INTO "UMBRAL" VALUES ('${PEDRO}', 'FC', now(), 40, 50, 110, 100)`, 'check');
await rechaza('Umbral de una variable que no existe (presión arterial)', `INSERT INTO "UMBRAL"(id_paciente, variable, vigente_desde) VALUES ('${PEDRO}', 'PRESION', now())`, 'enum');
await rechaza('Umbral de FC a medias (solo grave bajo y grave alto)', `INSERT INTO "UMBRAL" VALUES ('${PEDRO}', 'FC', now(), 120, null, null, 40)`, 'check');
await rechaza('Umbral de FC sin límite alto', `INSERT INTO "UMBRAL" VALUES ('${PEDRO}', 'FC', now(), 40, 50, null, null)`, 'check');
await rechaza('Umbral de SpO2 con límite alto', `INSERT INTO "UMBRAL" VALUES ('${PEDRO}', 'SPO2', now(), 85, 90, 150, null)`, 'check');
await rechaza('Umbral de SpO2 con normal desde 101 %', `INSERT INTO "UMBRAL" VALUES ('${PEDRO}', 'SPO2', now(), 95, 101, null, null)`, 'check');
await acepta('Umbral de FC completo y coherente para Pedro (se deshace)', `BEGIN; INSERT INTO "UMBRAL" VALUES ('${PEDRO}', 'FC', now(), 40, 50, 100, 120); ROLLBACK`);

// =====================================================================
nuevoGrupo('7. Relojes y asignaciones');
await acepta('Registro de cinco relojes con su SIM (uno retirado)', `INSERT INTO "SMARTWATCH"(id_dispositivo, identificador_reloj, iccid, numero_sim, fecha_vencimiento_plan, estado) VALUES
  ('${W1}', 'W-001', 'icc-1', '+56961', '2027-01-01', 'OPERATIVO'), ('${W2}', 'W-002', 'icc-2', '+56962', '2027-01-01', 'OPERATIVO'),
  ('${W3}', 'W-003', 'icc-3', '+56963', '2027-01-01', 'OPERATIVO'), ('${W4}', 'W-004', 'icc-4', '+56964', '2027-01-01', 'OPERATIVO'),
  ('${W5}', 'W-005', 'icc-5', null, null, 'RETIRADO')`);
await rechaza('Dos relojes con la misma SIM (ICCID)', `INSERT INTO "SMARTWATCH"(id_dispositivo, identificador_reloj, iccid, estado) VALUES (gen_random_uuid(), 'W-009', 'icc-1', 'OPERATIVO')`, 'unico');
await rechaza('Dos relojes con el mismo identificador', `INSERT INTO "SMARTWATCH"(id_dispositivo, identificador_reloj, estado) VALUES (gen_random_uuid(), 'W-001', 'OPERATIVO')`, 'unico');
await rechaza('Reloj sin estado', `INSERT INTO "SMARTWATCH"(id_dispositivo, identificador_reloj) VALUES (gen_random_uuid(), 'W-009')`, 'nulo');
await vale('Relojes con el plan vencido al 20-10-2026', `SELECT count(*) n FROM "SMARTWATCH" WHERE fecha_vencimiento_plan < '2026-10-20'`, 0);
await acepta('Asignación de un reloj a cada paciente', `INSERT INTO "ASIGNACION_RELOJ"(id_asignacion, id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj) VALUES
  ('${A_MARIA}', '${W1}', '${MARIA}', '${T1}', 'tok-1'), ('${A_PEDRO}', '${W2}', '${PEDRO}', '${T1}', 'tok-2'), ('${A_ROSA_VIEJA}', '${W3}', '${ROSA}', '${T1}', 'tok-3')`);
await acepta('Lote del reloj antiguo de Rosa', `INSERT INTO "SINCRONIZACION"(id_sincronizacion, event_id, id_asignacion, hash_configuracion, fecha_hora_recepcion, nivel_bateria) VALUES ('${S_ROSA_VIEJA}', gen_random_uuid(), '${A_ROSA_VIEJA}', 'h1', '${T2}', 40)`);
await acepta('Reemplazo del reloj de Rosa: se cierra la asignación y se abre otra', `UPDATE "ASIGNACION_RELOJ" SET fecha_fin = '2026-10-12 12:00Z' WHERE id_asignacion = '${A_ROSA_VIEJA}';
  INSERT INTO "ASIGNACION_RELOJ"(id_asignacion, id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj) VALUES ('${A_ROSA}', '${W4}', '${ROSA}', '2026-10-12 12:00Z', 'tok-4')`);
await vale('Rosa conserva el historial: 2 asignaciones, 1 vigente', `SELECT count(*) || '/' || count(*) FILTER (WHERE fecha_fin IS NULL) n FROM "ASIGNACION_RELOJ" WHERE id_paciente = '${ROSA}'`, '2/1');
await vale('El token del reloj antiguo ya no vale', `SELECT count(*) n FROM "ASIGNACION_RELOJ" WHERE hash_token_reloj = 'tok-3' AND fecha_fin IS NULL`, 0);
await vale('El lote antiguo sigue ligado a Rosa', `SELECT a.id_paciente = '${ROSA}' n FROM "SINCRONIZACION" s JOIN "ASIGNACION_RELOJ" a USING (id_asignacion) WHERE s.id_sincronizacion = '${S_ROSA_VIEJA}'`, true);
await vale('Relojes libres para asignar (operativos y sin asignación vigente)', `SELECT count(*) n FROM "SMARTWATCH" w WHERE estado = 'OPERATIVO'
  AND NOT EXISTS (SELECT 1 FROM "ASIGNACION_RELOJ" a WHERE a.id_dispositivo = w.id_dispositivo AND a.fecha_fin IS NULL)`, 1);
const asignacion = (reloj, pac, ini, fin = null, token = 'tok-x') =>
  `INSERT INTO "ASIGNACION_RELOJ"(id_asignacion, id_dispositivo, id_paciente, fecha_inicio, fecha_fin, hash_token_reloj)
   VALUES (gen_random_uuid(), '${reloj}', '${pac}', '${ini}', ${fin ? `'${fin}'` : 'null'}, '${token}')`;
await rechaza('Un reloj asignado a dos pacientes a la vez', asignacion(W1, PEDRO, '2026-10-20'), 'exclusion');
await rechaza('Un paciente con dos relojes vigentes', asignacion(W3, MARIA, '2026-10-20'), 'exclusion');
await rechaza('Reusar el token de una asignación cerrada', asignacion(W3, ROSA, '2026-10-20', '2026-10-21', 'tok-3'), 'unico');
await rechaza('Asignación sin token', `INSERT INTO "ASIGNACION_RELOJ"(id_asignacion, id_dispositivo, id_paciente, fecha_inicio) VALUES (gen_random_uuid(), '${W3}', '${MARIA}', '2020-01-01')`, 'nulo');
await rechaza('Asignación que termina antes de empezar', asignacion(W3, MARIA, '2026-05-02', '2026-05-01'), 'check');
await rechaza('Una asignación cerrada del reloj antiguo de Rosa que se superpone con su historial (a otro paciente)', asignacion(W3, PEDRO, '2026-10-05', '2026-10-06'), 'exclusion');
await rechaza('Una asignación cerrada de Rosa que se superpone con sus relojes', asignacion(W5, ROSA, '2026-10-11', '2026-10-13'), 'exclusion');
const pacienteNuevo = `INSERT INTO "PACIENTE"(id_paciente, rut, rut_hash, fecha_consentimiento, fecha_ingreso) VALUES ('${id(18)}', 'c', 'rut-18', '2026-10-01', '2026-10-01')`;
await acepta('El reloj antiguo de Rosa se asigna a otro paciente desde el momento en que se cerró (se deshace)',
  `BEGIN; ${pacienteNuevo}; ${asignacion(W3, id(18), '2026-10-12 12:00Z')}; ROLLBACK`);
await backend('Asignar un reloj RETIRADO', `${pacienteNuevo}; ${asignacion(W5, id(18), '2026-10-20')}`,
  'Nota de ASIGNACION_RELOJ: "solo se asignan relojes OPERATIVO, a pacientes con consentimiento y sin baja".');
await backend('Asignar un reloj a un paciente sin consentimiento firmado', `INSERT INTO "PACIENTE"(id_paciente, rut, rut_hash, fecha_ingreso) VALUES ('${id(19)}', 'c', 'rut-sc', '2026-10-01'); ${asignacion(W3, id(19), '2026-10-20')}`,
  'Notas de PACIENTE y ASIGNACION_RELOJ: sin fecha_consentimiento no se le asigna reloj.');
await backend('Dar de baja a un paciente que tiene un reloj vigente', `UPDATE "PACIENTE" SET fecha_baja = '2026-10-20' WHERE id_paciente = '${PEDRO}'`,
  'Nota de PACIENTE: "al darlo de baja se cierra su asignación vigente".');

// =====================================================================
nuevoGrupo('8. Datos recibidos del reloj (lotes, lecturas, GPS)');
await acepta('Lotes de cada reloj (LTE y Wi-Fi)', `INSERT INTO "SINCRONIZACION"(id_sincronizacion, event_id, id_asignacion, hash_configuracion, fecha_hora_envio, fecha_hora_recepcion, nivel_bateria, reloj_puesto, minutos_puesto, tipo_conexion, version_app) VALUES
  ('${S_MARIA}', gen_random_uuid(), '${A_MARIA}', 'h1', '2026-10-15 13:00Z', '2026-10-15 13:01Z', 80, true, 110, 'LTE', '1.0.0'),
  ('${S_PEDRO}', gen_random_uuid(), '${A_PEDRO}', 'h1', '2026-10-15 13:00Z', '2026-10-15 13:01Z', 15, true, 120, 'WIFI', '1.0.0'),
  ('${S_ROSA}', gen_random_uuid(), '${A_ROSA}', 'h1', '2026-10-15 13:00Z', '2026-10-15 13:01Z', 95, false, 30, 'LTE', '1.0.0')`);
const lectura = (lote, asig, tipo, valor, hora) =>
  `INSERT INTO "REGISTRO_VITAL"(id_registro, id_sincronizacion, id_asignacion, tipo, valor, fecha_hora_medicion) VALUES (gen_random_uuid(), '${lote}', '${asig}', '${tipo}', ${valor}, '${hora}')`;
await acepta('Lecturas de FC y SpO2 con su hora de medición', [
  lectura(S_MARIA, A_MARIA, 'FC', 70, '2026-10-15 12:00Z'), lectura(S_MARIA, A_MARIA, 'FC', 80, '2026-10-15 12:30Z'),
  lectura(S_MARIA, A_MARIA, 'FC', 90, '2026-10-15 13:00Z'), lectura(S_MARIA, A_MARIA, 'SPO2', 97, '2026-10-15 12:00Z'),
  lectura(S_PEDRO, A_PEDRO, 'SPO2', 85, '2026-10-15 12:00Z'), lectura(S_PEDRO, A_PEDRO, 'FC', 75, '2026-10-15 12:00Z')].join(';'));
await rechaza('Lectura con una asignación distinta a la de su lote (redundancia controlada)', lectura(S_MARIA, A_PEDRO, 'FC', 70, '2026-10-15 14:00Z'), 'foranea');
await acepta('Segundo lote de María, que reenvía historial', `INSERT INTO "SINCRONIZACION"(id_sincronizacion, event_id, id_asignacion, hash_configuracion, fecha_hora_recepcion, nivel_bateria) VALUES ('${S_MARIA_2}', gen_random_uuid(), '${A_MARIA}', 'h1', '2026-10-15 15:01Z', 70)`);
await rechaza('Lectura reenviada en otro lote', lectura(S_MARIA_2, A_MARIA, 'FC', 70, '2026-10-15 12:00Z'), 'unico');
await filas('El backend ignora la lectura reenviada (ON CONFLICT DO NOTHING)', lectura(S_MARIA_2, A_MARIA, 'FC', 70, '2026-10-15 12:00Z') + ' ON CONFLICT DO NOTHING', 0);
await rechaza('Lectura de un tipo que no existe (presión arterial)', lectura(S_MARIA, A_MARIA, 'PRESION', 120, '2026-10-15 14:00Z'), 'enum');
await rechaza('Lectura de un lote que no existe', lectura(id(99), A_MARIA, 'FC', 70, '2026-10-15 14:00Z'), 'foranea');
await acepta('Ubicación GPS de María', `INSERT INTO "UBICACION"(id_ubicacion, id_sincronizacion, id_asignacion, latitud, longitud, precision_metros, fecha_hora_medicion) VALUES (gen_random_uuid(), '${S_MARIA}', '${A_MARIA}', -33.45, -70.66, 12, '2026-10-15 12:00Z')`);
await rechaza('Ubicación repetida para la misma hora', `INSERT INTO "UBICACION"(id_ubicacion, id_sincronizacion, id_asignacion, latitud, longitud, fecha_hora_medicion) VALUES (gen_random_uuid(), '${S_MARIA_2}', '${A_MARIA}', -33.45, -70.66, '2026-10-15 12:00Z')`, 'unico');
await rechaza('Ubicación con una asignación distinta a la de su lote', `INSERT INTO "UBICACION"(id_ubicacion, id_sincronizacion, id_asignacion, latitud, longitud, fecha_hora_medicion) VALUES (gen_random_uuid(), '${S_MARIA}', '${A_PEDRO}', -33.45, -70.66, '2026-10-15 14:00Z')`, 'foranea');
await acepta('Eventos descartados por el reloj, por tipo', `INSERT INTO "EVENTO_DESCARTADO" VALUES ('${S_MARIA}', 'SPO2', 3), ('${S_MARIA}', 'CAIDA', 1), ('${S_PEDRO}', 'SPO2', 2)`);
await rechaza('Contador de descartes en cero', `INSERT INTO "EVENTO_DESCARTADO" VALUES ('${S_ROSA}', 'FC', 0)`, 'check');
await rechaza('Contador de descartes repetido para el mismo tipo', `INSERT INTO "EVENTO_DESCARTADO" VALUES ('${S_MARIA}', 'SPO2', 1)`, 'unico');
await rechaza('El reloj informa descartes de un evento que solo crea el servidor (SILENCIO_DATOS)', `INSERT INTO "EVENTO_DESCARTADO" VALUES ('${S_ROSA}', 'SILENCIO_DATOS', 1)`, 'check');
await rechaza('Lote repetido (mismo event_id, el reloj reintentó)', `INSERT INTO "SINCRONIZACION"(id_sincronizacion, event_id, id_asignacion, fecha_hora_recepcion) SELECT gen_random_uuid(), event_id, id_asignacion, now() FROM "SINCRONIZACION" WHERE id_sincronizacion = '${S_MARIA}'`, 'unico');
await rechaza('Batería de 150 %', `UPDATE "SINCRONIZACION" SET nivel_bateria = 150 WHERE id_sincronizacion = '${S_MARIA}'`, 'check');
await rechaza('Minutos puesto negativos', `UPDATE "SINCRONIZACION" SET minutos_puesto = -1 WHERE id_sincronizacion = '${S_MARIA}'`, 'check');
await vale('Estado actual del reloj de María (último lote): batería', `SELECT nivel_bateria n FROM "SINCRONIZACION" WHERE id_asignacion = '${A_MARIA}' ORDER BY fecha_hora_recepcion DESC LIMIT 1`, 70);

// =====================================================================
nuevoGrupo('9. Configuración pendiente en el reloj (por hash)');
await pendiente('Pedro: el reloj tiene h1 y el backend calcula h1', A_PEDRO, 'h1', 'false');
await pendiente('María cambió su configuración: el backend calcula h2 y el reloj sigue en h1', A_MARIA, 'h2', 'true');
await acepta('El reloj de María aplica la configuración nueva y la informa en su siguiente lote', `INSERT INTO "SINCRONIZACION"(id_sincronizacion, event_id, id_asignacion, hash_configuracion, fecha_hora_recepcion, nivel_bateria) VALUES (gen_random_uuid(), gen_random_uuid(), '${A_MARIA}', 'h2', '2026-10-15 17:01Z', 68)`);
await pendiente('María al día', A_MARIA, 'h2', 'false');
await pendiente('Un reloj que aún no envía lotes queda pendiente', id(99), 'h1', 'true');

// =====================================================================
nuevoGrupo('10. Flujo completo de una alerta (caída de María)');
await acepta('Llega una caída MEDIA del reloj, con su evidencia', alertaReloj(CAIDA_MARIA, A_MARIA, 'CAIDA', 'MEDIA', ', evidencia, fecha_hora_envio',
  `, '{"aceleracion": 3.2, "version_matriz": "2026-10"}', '2026-10-15 03:00:30-03'`));
await estado('Estado inicial', CAIDA_MARIA, 'RECIBIDA', 'SIN_ASIGNAR');
await vale('Se asigna un folio automáticamente', `SELECT (numero IS NOT NULL)::text n FROM "ALERTA" WHERE id_alerta = '${CAIDA_MARIA}'`, 'true');
await vale('Retraso por cobertura (recepción menos detección) se calcula', `SELECT (fecha_hora_recepcion - fecha_hora_deteccion > interval '0')::text n FROM "ALERTA" WHERE id_alerta = '${CAIDA_MARIA}'`, 'true');
await acepta('El reloj pide avisar a la hija (prioridad 1)', aviso(CAIDA_MARIA, M1, 1, ', id_externo', `, 'wamid.1'`));
await estado('Estado al enviar el primer aviso', CAIDA_MARIA, 'AVISANDO_CONTACTO', 'SIN_ASIGNAR');
await acepta('Meta informa entrega y lectura (webhook)', `UPDATE "REGISTRO_INTENTO" SET fecha_hora_entrega = now(), fecha_hora_lectura = now() WHERE id_externo = 'wamid.1'`);
await vale('Estado de entrega calculado desde las fechas', `SELECT CASE WHEN fecha_hora_fallo IS NOT NULL THEN 'FALLIDO' WHEN fecha_hora_lectura IS NOT NULL THEN 'LEIDO'
  WHEN fecha_hora_entrega IS NOT NULL THEN 'ENTREGADO' ELSE 'ENVIADO' END n FROM "REGISTRO_INTENTO" WHERE id_externo = 'wamid.1'`, 'LEIDO');
await acepta('La hija responde "No puedo"', `UPDATE "REGISTRO_INTENTO" SET respuesta = 'NO_PUEDO', fecha_hora_respuesta = now() WHERE id_externo = 'wamid.1'`);
await estado('Sigue avisando', CAIDA_MARIA, 'AVISANDO_CONTACTO', 'SIN_ASIGNAR');
await rechaza('Segundos de inmovilidad sin agravamiento', `UPDATE "ALERTA" SET segundos_inmovilidad_agravamiento = 90 WHERE id_alerta = '${CAIDA_MARIA}'`, 'check');
await acepta('La caída se agrava por inmovilidad (90 s sin moverse)', `UPDATE "ALERTA" SET fecha_hora_agravamiento = now(), segundos_inmovilidad_agravamiento = 90 WHERE id_alerta = '${CAIDA_MARIA}'`);
await rechaza('Segundos de inmovilidad negativos', `UPDATE "ALERTA" SET segundos_inmovilidad_agravamiento = -1 WHERE id_alerta = '${CAIDA_MARIA}'`, 'check');
await acepta('La caída llegó con una discrepancia: el reloj usaba otra versión de la configuración', `INSERT INTO "DISCREPANCIA_ALERTA"(id_alerta, codigo, detalle) VALUES ('${CAIDA_MARIA}', 'VERSION_DISTINTA', 'El reloj usó la versión h0; la vigente es h1')`);
await rechaza('La misma discrepancia dos veces en la alerta', `INSERT INTO "DISCREPANCIA_ALERTA"(id_alerta, codigo, detalle) VALUES ('${CAIDA_MARIA}', 'VERSION_DISTINTA', 'otra vez')`, 'unico');
await rechaza('Discrepancia con un código que no existe', `INSERT INTO "DISCREPANCIA_ALERTA"(id_alerta, codigo, detalle) VALUES ('${CAIDA_MARIA}', 'HORA_RARA', 'x')`, 'enum');
await rechaza('Discrepancia de una alerta que no existe', `INSERT INTO "DISCREPANCIA_ALERTA"(id_alerta, codigo, detalle) VALUES ('${id(98)}', 'MODULO_APAGADO', 'x')`, 'foranea');
await vale('Conserva la gravedad con que llegó', `SELECT severidad n FROM "ALERTA" WHERE id_alerta = '${CAIDA_MARIA}'`, 'MEDIA');
await vale('Gravedad actual calculada', `SELECT severidad_actual n FROM "ALERTA_ESTADO" WHERE id_alerta = '${CAIDA_MARIA}'`, 'GRAVE');
await acepta('El reloj pasa a Seguridad Ciudadana (prioridad 2), que responde "Voy en camino"', aviso(CAIDA_MARIA, M2, 1, ', id_externo, respuesta, fecha_hora_respuesta', `, 'wamid.2', 'VOY_EN_CAMINO', now()`));
await estado('Estado tras la confirmación', CAIDA_MARIA, 'CONFIRMADA', 'SIN_ASIGNAR');
await acepta('Aviso FCM al reloj y el reloj informa que lo recibió', `INSERT INTO "AVISO_FCM"(id_aviso, id_alerta, tipo, fecha_hora_envio, intentos) VALUES (gen_random_uuid(), '${CAIDA_MARIA}', 'ALERTA_CONFIRMADA', now(), 1);
  UPDATE "AVISO_FCM" SET fecha_hora_recepcion = now() WHERE id_alerta = '${CAIDA_MARIA}'`);
await vale('Contacto que confirmó (para la pantalla del reloj)', `SELECT k.nombre n FROM "REGISTRO_INTENTO" i JOIN "CONTACTO_PACIENTE" cp USING (id_contacto_paciente) JOIN "CONTACTO" k USING (id_contacto)
  WHERE i.id_alerta = '${CAIDA_MARIA}' AND i.respuesta = 'VOY_EN_CAMINO'`, 'Seguridad Ciudadana');
await filas('Ana asume el caso', asumir(CAIDA_MARIA, ANA, '2026-10-15 08:05Z'), 1);
await estado('Estado al asumir', CAIDA_MARIA, 'CONFIRMADA', 'EN_ATENCION');
await filas('Luis intenta asumirlo un momento después: no afecta filas (ya lo tomó Ana)', asumir(CAIDA_MARIA, LUIS, '2026-10-15 08:06Z'), 0);
await vale('La responsable sigue siendo Ana', `SELECT nombre_profesional n FROM "ATENCION_ALERTA" JOIN "USUARIO" ON id_usuario = id_usuario_responsable WHERE id_alerta = '${CAIDA_MARIA}'`, 'Ana');
await acepta('Ana la resuelve como POSITIVA y queda a cargo del seguimiento', `UPDATE "ATENCION_ALERTA" SET fecha_hora_cierre = '2026-10-15 09:00Z', id_usuario_resolutor = '${ANA}',
  clasificacion = 'POSITIVA', observaciones_cierre = 'Caída real, sin lesiones' WHERE id_alerta = '${CAIDA_MARIA}'`);
await estado('Estado tras el cierre', CAIDA_MARIA, 'RESUELTA', 'EN_SEGUIMIENTO');
await acepta('Reasignación del seguimiento a Luis', `UPDATE "ATENCION_ALERTA" SET id_usuario_responsable = '${LUIS}' WHERE id_alerta = '${CAIDA_MARIA}'`);
await acepta('Catálogo de destinos de derivación', `INSERT INTO "DESTINO_DERIVACION"(codigo, nombre) VALUES ('CESFAM', 'CESFAM'), ('SAMU', 'SAMU')`);
await acepta('Llamada y derivación al CESFAM', `INSERT INTO "SEGUIMIENTO"(id_seguimiento, id_alerta, id_usuario, tipo, codigo_destino, nota, fecha_hora) VALUES
  (gen_random_uuid(), '${CAIDA_MARIA}', '${LUIS}', 'LLAMADA', null, 'Se llama a la hija', '2026-10-16 10:00Z'),
  (gen_random_uuid(), '${CAIDA_MARIA}', '${LUIS}', 'DERIVACION', 'CESFAM', 'Control kinesiológico', '2026-10-16 11:00Z')`);
await vale('"Derivada" se ve en el seguimiento (no es un estado)', `SELECT count(*) n FROM "SEGUIMIENTO" WHERE id_alerta = '${CAIDA_MARIA}' AND tipo = 'DERIVACION'`, 1);
await rechaza('Derivación sin destino', `INSERT INTO "SEGUIMIENTO"(id_seguimiento, id_alerta, id_usuario, tipo, fecha_hora) VALUES (gen_random_uuid(), '${CAIDA_MARIA}', '${LUIS}', 'DERIVACION', now())`, 'check');
await rechaza('Llamada con destino de derivación', `INSERT INTO "SEGUIMIENTO"(id_seguimiento, id_alerta, id_usuario, tipo, codigo_destino, fecha_hora) VALUES (gen_random_uuid(), '${CAIDA_MARIA}', '${LUIS}', 'LLAMADA', 'SAMU', now())`, 'check');
await rechaza('Derivación a un destino que no existe', `INSERT INTO "SEGUIMIENTO"(id_seguimiento, id_alerta, id_usuario, tipo, codigo_destino, fecha_hora) VALUES (gen_random_uuid(), '${CAIDA_MARIA}', '${LUIS}', 'DERIVACION', 'BOMBEROS', now())`, 'foranea');
await rechaza('Cierre del seguimiento antes del cierre de la alerta', `UPDATE "ATENCION_ALERTA" SET fecha_cierre_seguimiento = '2026-10-15 08:00Z' WHERE id_alerta = '${CAIDA_MARIA}'`, 'check');
await acepta('Cierre del seguimiento', `UPDATE "ATENCION_ALERTA" SET fecha_cierre_seguimiento = '2026-10-17 12:00Z' WHERE id_alerta = '${CAIDA_MARIA}'`);
await estado('Estado final', CAIDA_MARIA, 'RESUELTA', 'RESUELTA');
await backend('Agregar un seguimiento después del cierre del seguimiento', `INSERT INTO "SEGUIMIENTO"(id_seguimiento, id_alerta, id_usuario, tipo, fecha_hora) VALUES (gen_random_uuid(), '${CAIDA_MARIA}', '${LUIS}', 'NOTA', '2026-10-18 10:00Z')`,
  'Nota de SEGUIMIENTO: "No se agrega seguimiento después de fecha_cierre_seguimiento (lo valida el backend)".');
await vale('Historial del caso (avisos + FCM + seguimientos)', `SELECT (SELECT count(*) FROM "REGISTRO_INTENTO" WHERE id_alerta = '${CAIDA_MARIA}')
  + (SELECT count(*) FROM "AVISO_FCM" WHERE id_alerta = '${CAIDA_MARIA}') + (SELECT count(*) FROM "SEGUIMIENTO" WHERE id_alerta = '${CAIDA_MARIA}') n`, 5);
await backend('El equipo cambia la gravedad con que llegó la alerta', `UPDATE "ALERTA" SET severidad = 'LEVE' WHERE id_alerta = '${CAIDA_MARIA}'`,
  'Nota de ALERTA: se controla con permisos de la base (el usuario del dashboard no tiene UPDATE sobre ALERTA). Las pruebas corren como superusuario, por eso la base lo acepta aquí.');

// =====================================================================
nuevoGrupo('11. Reglas de la atención (ATENCION_ALERTA)');
const atencion = (cols, vals, alerta = MEDIA_ROSA) => `INSERT INTO "ATENCION_ALERTA"(id_alerta${cols}) VALUES ('${alerta}'${vals})`;
await acepta('Media de madrugada (Rosa): avisa de inmediato a Seguridad Ciudadana (compartido)', alertaReloj(MEDIA_ROSA, A_ROSA, 'FC_SPO2', 'MEDIA') + ';' + aviso(MEDIA_ROSA, R1));
await rechaza('Atención vacía (sin toma ni cierre)', atencion('', ''), 'check');
await rechaza('Toma sin responsable', atencion(', fecha_hora_toma', ', now()'), 'check');
await rechaza('Responsable sin hora de toma', atencion(', id_usuario_responsable', `, '${ANA}'`), 'check');
await rechaza('Clasificar sin cerrar', atencion(', id_usuario_responsable, fecha_hora_toma, clasificacion', `, '${ANA}', now(), 'FALSA_ALARMA'`), 'check');
await rechaza('Observaciones de cierre sin cerrar', atencion(', id_usuario_responsable, fecha_hora_toma, observaciones_cierre', `, '${ANA}', now(), 'x'`), 'check');
await rechaza('Cierre anterior a la toma', atencion(', id_usuario_responsable, fecha_hora_toma, fecha_hora_cierre', `, '${ANA}', '2026-10-15 10:00Z', '2026-10-15 09:00Z'`), 'check');
await rechaza('POSITIVA sin responsable del seguimiento', atencion(', fecha_hora_cierre, id_usuario_resolutor, clasificacion', `, now(), '${ANA}', 'POSITIVA'`), 'check');
await rechaza('Cierre de seguimiento en una falsa alarma', atencion(', fecha_hora_cierre, clasificacion, fecha_cierre_seguimiento', `, now(), 'FALSA_ALARMA', now()`), 'check');
await rechaza('Responsable que no existe', atencion(', id_usuario_responsable, fecha_hora_toma', `, gen_random_uuid(), now()`), 'foranea');
await rechaza('Clasificación que no existe', atencion(', fecha_hora_cierre, clasificacion', `, now(), 'DUDOSA'`), 'enum');
await rechaza('Dos atenciones para la misma alerta', atencion(', id_usuario_responsable, fecha_hora_toma', `, '${ANA}', now()`, CAIDA_MARIA), 'unico');
await rechaza('Atención de una alerta que no existe', atencion(', id_usuario_responsable, fecha_hora_toma', `, '${ANA}', now()`, id(98)), 'foranea');

// =====================================================================
nuevoGrupo('12. Gravedad y horario (leve, media y grave de madrugada)');
await acepta('Leve de madrugada (Pedro): el reloj no avisa y espera el día hábil', alertaReloj(LEVE_PEDRO, A_PEDRO, 'FC', 'LEVE', ', espera_dia_habil', ', true'));
await estado('Queda en la bandeja de día hábil', LEVE_PEDRO, 'PENDIENTE_DIA_HABIL', 'SIN_ASIGNAR');
await vale('El folio crece con cada alerta', `SELECT (b.numero > a.numero)::text n FROM "ALERTA" a, "ALERTA" b WHERE a.id_alerta = '${CAIDA_MARIA}' AND b.id_alerta = '${LEVE_PEDRO}'`, 'true');
await acepta('A primera hora, Ana la cierra directamente como falsa alarma (sin asumirla antes)', atencion(', fecha_hora_cierre, id_usuario_resolutor, clasificacion, observaciones_cierre',
  `, now(), '${ANA}', 'FALSA_ALARMA', 'Lectura por movimiento'`, LEVE_PEDRO));
await estado('Estado tras la revisión', LEVE_PEDRO, 'RESUELTA', 'RESUELTA');
await estado('La media está avisando', MEDIA_ROSA, 'AVISANDO_CONTACTO', 'SIN_ASIGNAR');
await acepta('Grave de madrugada (Rosa): el reloj recorre la lista dos veces', alertaReloj(GRAVE_ROSA, A_ROSA, 'SPO2', 'GRAVE') + ';'
  + aviso(GRAVE_ROSA, R1, 1) + ';' + aviso(GRAVE_ROSA, R2, 1, '', '', 'CORREO') + ';' + aviso(GRAVE_ROSA, R1, 2) + ';' + aviso(GRAVE_ROSA, R2, 2, '', '', 'CORREO'));
await acepta('Nadie confirma: el reloj informa el fin de la lista', `UPDATE "ALERTA" SET fecha_hora_sin_respuesta = now() WHERE id_alerta = '${GRAVE_ROSA}'`);
await estado('Estado de la grave sin respuesta', GRAVE_ROSA, 'SIN_RESPUESTA', 'SIN_ASIGNAR');
await rechaza('Una media marcada para esperar el día hábil', alertaReloj(id(90), A_ROSA, 'FC', 'MEDIA', ', espera_dia_habil', ', true'), 'check');
await rechaza('Una grave marcada para esperar el día hábil', alertaReloj(id(91), A_ROSA, 'FC', 'GRAVE', ', espera_dia_habil', ', true'), 'check');
await rechaza('Gravedad que no existe (MODERADA)', alertaReloj(id(92), A_ROSA, 'FC', 'MODERADA'), 'enum');
await rechaza('Un botón de ayuda que no es leve', alertaReloj(id(93), A_ROSA, 'BOTON_AYUDA', 'MEDIA'), 'check');
await acepta('Botón de ayuda de madrugada: leve, espera el día hábil (se deshace)', 'BEGIN; ' + alertaReloj(id(93), A_ROSA, 'BOTON_AYUDA', 'LEVE', ', espera_dia_habil', ', true') + '; ROLLBACK');
await acepta('Salida de la zona segura con la gravedad de la zona (se deshace)', 'BEGIN; ' + alertaReloj(id(93), A_ROSA, 'ZONA_SEGURA', 'GRAVE') + '; ROLLBACK');
await rechaza('Agravar una alerta que llegó grave', `UPDATE "ALERTA" SET fecha_hora_agravamiento = now() WHERE id_alerta = '${GRAVE_ROSA}'`, 'check');
await rechaza('Agravar una alerta que no es caída', `UPDATE "ALERTA" SET fecha_hora_agravamiento = now() WHERE id_alerta = '${MEDIA_ROSA}'`, 'check');
await rechaza('Tercera ronda de avisos', aviso(GRAVE_ROSA, R1, 3), 'check');

// =====================================================================
nuevoGrupo('13. Cancelación por el paciente y simulacro');
await acepta('El paciente cancela una caída en los 30 segundos', alertaReloj(CANCELADA, A_PEDRO, 'CAIDA', 'GRAVE', ', fecha_hora_cancelacion', ', now()'));
await estado('Estado de la cancelada', CANCELADA, 'CANCELADA', 'SIN_ASIGNAR');
await rechaza('Una cancelada no puede quedar sin respuesta', `UPDATE "ALERTA" SET fecha_hora_sin_respuesta = now() WHERE id_alerta = '${CANCELADA}'`, 'check');
await backend('Una cancelada recibe atención del equipo', atencion(', id_usuario_responsable, fecha_hora_toma', `, '${ANA}', now()`, CANCELADA),
  'Nota de ALERTA: "Que una alerta CANCELADA no tenga atención lo valida el backend".');
await backend('Una cancelada tiene avisos a contactos', aviso(CANCELADA, P2),
  'Notas de ALERTA y REGISTRO_INTENTO: una alerta cancelada no tiene avisos a contactos.');
await acepta('Simulacro desde el dashboard, con aviso FCM', alertaServidor(SIMULACRO, A_PEDRO, 'SILENCIO_DATOS', ', es_simulacro', ', true')
  + `; INSERT INTO "AVISO_FCM"(id_aviso, id_alerta, tipo) VALUES (gen_random_uuid(), '${SIMULACRO}', 'SIMULACRO')`);
await rechaza('Avisos FCM con intentos negativos', `UPDATE "AVISO_FCM" SET intentos = -1 WHERE id_alerta = '${SIMULACRO}'`, 'check');

// =====================================================================
nuevoGrupo('14. Alertas técnicas del servidor');
const abiertaServidor = (asig, tipo) => `SELECT count(*) n FROM "ALERTA" a LEFT JOIN "ATENCION_ALERTA" t USING (id_alerta)
  WHERE a.id_asignacion = '${asig}' AND a.tipo_evento = '${tipo}' AND a.origen = 'SERVIDOR' AND NOT a.es_simulacro AND t.fecha_hora_cierre IS NULL`;
await acepta('Bloqueo de la nota de ALERTA: pg_advisory_xact_lock(hashtextextended(id_asignacion::text || \':\' || tipo_evento, 0))',
  `BEGIN; SELECT pg_advisory_xact_lock(hashtextextended('${A_PEDRO}'::uuid::text || ':' || 'SILENCIO_DATOS'::tipo_evento, 0)); COMMIT`);
await acepta('Silencio de datos del reloj de Pedro', alertaServidor(SILENCIO, A_PEDRO, 'SILENCIO_DATOS'));
await vale('Hay un silencio de datos abierto: el job no crea otro', abiertaServidor(A_PEDRO, 'SILENCIO_DATOS'), 1);
await acepta('Vuelven los datos: el sistema la cierra solo (sin profesional)', atencion(', fecha_hora_cierre', `, '2026-10-16 10:00Z'`, SILENCIO));
await estado('Estado de la técnica cerrada por el sistema', SILENCIO, 'RESUELTA', 'RESUELTA');
await vale('Ya no hay silencio abierto', abiertaServidor(A_PEDRO, 'SILENCIO_DATOS'), 0);
await filas('Alguien intenta asumir la técnica ya cerrada con la receta de la nota: no afecta filas', asumir(SILENCIO, ANA, '2026-10-16 11:00Z'), 0);
await acepta('Después puede abrirse un nuevo silencio de datos', alertaServidor(SILENCIO_2, A_PEDRO, 'SILENCIO_DATOS'));
await acepta('El reloj envía otra alerta del mismo tipo con una abierta (nunca se descarta)', alertaReloj(GRAVE_ROSA_2, A_ROSA, 'SPO2', 'GRAVE'));
await backend('Clasificar como POSITIVA una alerta técnica', atencion(', id_usuario_responsable, fecha_hora_toma, fecha_hora_cierre, clasificacion', `, '${ANA}', now(), now(), 'POSITIVA'`, SILENCIO_2),
  'Nota de ATENCION_ALERTA: "Solo alertas clínicas (lo valida el backend con ALERTA.tipo_evento)".');
await rechaza('Alerta del servidor que dice traer configuración del reloj', `INSERT INTO "ALERTA"(id_alerta, event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad, fecha_hora_recepcion)
  VALUES (gen_random_uuid(), gen_random_uuid(), '${A_PEDRO}', 'h1', 'SERVIDOR', 'BATERIA_BAJA', 'LEVE', now())`, 'check');
await rechaza('Alerta del reloj sin el hash de su configuración', `INSERT INTO "ALERTA"(id_alerta, event_id, id_asignacion, origen, tipo_evento, severidad, fecha_hora_recepcion)
  VALUES (gen_random_uuid(), gen_random_uuid(), '${A_PEDRO}', 'RELOJ', 'FC', 'LEVE', now())`, 'check');
await rechaza('El servidor crea una alerta clínica (CAIDA)', alertaServidor(id(96), A_PEDRO, 'CAIDA'), 'check');
await acepta('El servidor crea una alerta de batería baja (técnica, se deshace)', `BEGIN; ${alertaServidor(id(96), A_PEDRO, 'BATERIA_BAJA')}; ROLLBACK`);
await rechaza('El reloj informa un silencio de datos (solo lo puede detectar el servidor)', alertaReloj(id(97), A_PEDRO, 'SILENCIO_DATOS', 'LEVE'), 'check');
await acepta('El servidor crea una alerta por exceso de alertas del reloj (técnica, se deshace)', 'BEGIN; ' + alertaServidor(id(96), A_PEDRO, 'EXCESO_ALERTAS') + '; ROLLBACK');await rechaza('El reloj informa un exceso de alertas (solo lo puede detectar el servidor)', alertaReloj(id(97), A_PEDRO, 'EXCESO_ALERTAS', 'LEVE'), 'check');await vale('Una cuenta nueva no recibe avisos por correo hasta que se encienda', `SELECT column_default n FROM information_schema.columns WHERE table_name = 'USUARIO' AND column_name = 'recibe_avisos'`, 'false');

// =====================================================================
nuevoGrupo('15. Reintentos y mensajes duplicados (idempotencia)');
await rechaza('El reloj reenvía la misma alerta (mismo event_id)', `INSERT INTO "ALERTA"(id_alerta, event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad, fecha_hora_recepcion)
  SELECT gen_random_uuid(), event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad, now() FROM "ALERTA" WHERE id_alerta = '${CAIDA_MARIA}'`, 'unico');
await filas('El backend ignora la alerta reenviada (ON CONFLICT (event_id) DO NOTHING)', `INSERT INTO "ALERTA"(id_alerta, event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad, fecha_hora_recepcion)
  SELECT gen_random_uuid(), event_id, id_asignacion, hash_configuracion, origen, tipo_evento, severidad, now() FROM "ALERTA" WHERE id_alerta = '${CAIDA_MARIA}' ON CONFLICT (event_id) DO NOTHING`, 0);
await rechaza('El reloj repite una solicitud de aviso (no se envía dos veces)', `INSERT INTO "REGISTRO_INTENTO"(id_intento, event_id, id_alerta, id_contacto_paciente, canal, ronda)
  SELECT gen_random_uuid(), event_id, id_alerta, id_contacto_paciente, canal, ronda FROM "REGISTRO_INTENTO" WHERE id_externo = 'wamid.1'`, 'unico');
await rechaza('Meta repite un webhook (mismo id de mensaje)', aviso(CAIDA_MARIA, M1, 2, ', id_externo', `, 'wamid.1'`), 'unico');
await rechaza('Aviso FCM repetido del mismo tipo para la misma alerta', `INSERT INTO "AVISO_FCM"(id_aviso, id_alerta, tipo) VALUES (gen_random_uuid(), '${CAIDA_MARIA}', 'ALERTA_CONFIRMADA')`, 'unico');
await rechaza('Dos solicitudes distintas para el mismo contacto, canal y ronda (el reloj se reinició)', aviso(MEDIA_ROSA, R1, 1), 'unico');
await acepta('El mismo contacto en la ronda 2 (se deshace)', `BEGIN; ${aviso(MEDIA_ROSA, R1, 2)}; ROLLBACK`);

// =====================================================================
nuevoGrupo('16. Reglas de los avisos a contactos');
await rechaza('Respuesta del contacto sin su hora', aviso(MEDIA_ROSA, R2, 2, ', respuesta', `, 'NO_PUEDO'`), 'check');
await rechaza('Motivo de falla sin fecha de falla', aviso(MEDIA_ROSA, R2, 2, ', motivo_fallo', `, 'Número inválido'`), 'check');
await rechaza('Lectura informada en un correo (solo WhatsApp)', aviso(MEDIA_ROSA, R2, 2, ', fecha_hora_lectura', ', now()', 'CORREO'), 'check');
await rechaza('Respuesta que no existe en los botones', aviso(MEDIA_ROSA, R2, 2, ', respuesta, fecha_hora_respuesta', `, 'TAL_VEZ', now()`), 'enum');
await rechaza('Canal que no existe (SMS)', aviso(MEDIA_ROSA, R2, 2, '', '', 'SMS'), 'enum');
await rechaza('Aviso a un vínculo que no existe', aviso(MEDIA_ROSA, id(99)), 'foranea');
await backend('Aviso a un vínculo de otro paciente (la hija de María en una alerta de Rosa)', aviso(MEDIA_ROSA, M1, 2),
  'Nota de REGISTRO_INTENTO: "Que el vínculo sea del paciente de la alerta lo valida el backend".');
await backend('Aviso por WhatsApp a una persona que solo tiene correo (el vecino)', aviso(MEDIA_ROSA, R2, 2),
  'Nota de REGISTRO_INTENTO: "la persona tiene el canal pedido".');
await backend('Aviso por WhatsApp a una persona que revocó el opt-in (la cuidadora de Pedro)', aviso(LEVE_PEDRO, P1),
  'Nota de REGISTRO_INTENTO: "en WhatsApp, opt-in vigente".');
await acepta('Ana como contacto interno: solo el enlace a su cuenta, sin copiar sus datos',
  `INSERT INTO "CONTACTO"(id_contacto, id_usuario) VALUES ('${INTERNO_ANA}', '${ANA}')`);
await rechaza('Segundo contacto interno para la misma cuenta',
  `INSERT INTO "CONTACTO"(id_contacto, id_usuario) VALUES (gen_random_uuid(), '${ANA}')`, 'unico');
await rechaza('Contacto interno con nombre propio (se lee de la cuenta)',
  `INSERT INTO "CONTACTO"(id_contacto, id_usuario, nombre) VALUES (gen_random_uuid(), '${LUIS}', 'Luis')`, 'check');
await rechaza('Contacto interno con correo propio (se lee de la cuenta)',
  `INSERT INTO "CONTACTO"(id_contacto, id_usuario, correo, correo_hash) VALUES (gen_random_uuid(), '${LUIS}', 'x', 'c-interno')`, 'check');
await rechaza('Contacto externo sin nombre',
  `INSERT INTO "CONTACTO"(id_contacto, correo, correo_hash) VALUES (gen_random_uuid(), 'x', 'c-sin-nombre')`, 'check');
await rechaza('Contacto interno de una cuenta que no existe',
  `INSERT INTO "CONTACTO"(id_contacto, id_usuario) VALUES (gen_random_uuid(), '${id(99)}')`, 'foranea');
await rechaza('El contacto interno en la lista de un paciente (el reloj no recorre al equipo)',
  `INSERT INTO "CONTACTO_PACIENTE"(id_contacto_paciente, id_paciente, id_contacto, codigo_tipo_contacto, orden_prioridad)
   VALUES (gen_random_uuid(), '${ROSA}', '${INTERNO_ANA}', 'OTRO', 9)`, 'disparador');
await rechaza('Cambiar un vínculo de la lista para que apunte al contacto interno',
  `UPDATE "CONTACTO_PACIENTE" SET id_contacto = '${INTERNO_ANA}' WHERE id_contacto_paciente = '${R2}'`, 'disparador');
await rechaza('Convertir en interno a una persona de la lista',
  `UPDATE "CONTACTO" SET id_usuario = '${LUIS}', nombre = NULL, whatsapp = NULL, whatsapp_hash = NULL, correo = NULL,
   correo_hash = NULL, fecha_opt_in = NULL, fecha_revocacion_opt_in = NULL WHERE id_contacto = '${VECINO}'`, 'disparador');
await acepta('Correo del servidor a Ana por un silencio de datos, pendiente de envío',
  avisoInterno(SILENCIO_2, INTERNO_ANA, ', fecha_hora_proximo_envio', ', now()'));
await estado('Un aviso al equipo no cambia el estado de contacto (no es el recorrido del reloj)', SILENCIO_2, 'RECIBIDA', 'SIN_ASIGNAR');
await rechaza('Segundo correo a la misma cuenta por la misma alerta', avisoInterno(SILENCIO_2, INTERNO_ANA), 'unico');
await rechaza('Aviso interno dirigido a una persona de la lista de un paciente', avisoInterno(MEDIA_ROSA, SEG_CIUDADANA), 'disparador');
await rechaza('Aviso interno con ronda (las rondas son del recorrido del reloj)', avisoInterno(MEDIA_ROSA, INTERNO_ANA, ', ronda', ', 1'), 'check');
await rechaza('Aviso a un vínculo sin ronda',
  `INSERT INTO "REGISTRO_INTENTO"(id_intento, event_id, id_alerta, id_contacto_paciente, canal)
   VALUES (gen_random_uuid(), gen_random_uuid(), '${MEDIA_ROSA}', '${R2}', 'CORREO')`, 'check');
await rechaza('Aviso a un vínculo y a un contacto interno a la vez',
  aviso(MEDIA_ROSA, R2, 2, ', id_contacto_interno', `, '${INTERNO_ANA}'`, 'CORREO'), 'check');
await rechaza('Aviso sin destino',
  `INSERT INTO "REGISTRO_INTENTO"(id_intento, event_id, id_alerta, canal) VALUES (gen_random_uuid(), gen_random_uuid(), '${MEDIA_ROSA}', 'CORREO')`, 'check');
await rechaza('Respuesta "Voy en camino" de una cuenta del equipo (solo confirma un contacto del paciente)',
  avisoInterno(MEDIA_ROSA, INTERNO_ANA, ', respuesta, fecha_hora_respuesta', `, 'VOY_EN_CAMINO', now()`), 'check');
await rechaza('Reintentos negativos', avisoInterno(MEDIA_ROSA, INTERNO_ANA, ', reintentos', ', -1'), 'check');
await rechaza('Pendiente de envío pero ya enviado',
  aviso(MEDIA_ROSA, R2, 2, ', fecha_hora_proximo_envio', ', now()', 'CORREO'), 'check');
await rechaza('Pendiente de envío pero ya marcado como fallido',
  avisoInterno(MEDIA_ROSA, INTERNO_ANA, ', fecha_hora_proximo_envio, fecha_hora_fallo', ', now(), now()'), 'check');

// =====================================================================
nuevoGrupo('17. Datos calculados (reportes, hallazgos, panel)');
await vale('Reporte diario de María (hora de Chile): FC promedio / mínima / máxima', `SELECT round(avg(valor)) || ' / ' || min(valor) || ' / ' || max(valor) n FROM "REGISTRO_VITAL" r
  JOIN "ASIGNACION_RELOJ" a USING (id_asignacion)
  WHERE a.id_paciente = '${MARIA}' AND r.tipo = 'FC' AND (r.fecha_hora_medicion AT TIME ZONE 'America/Santiago')::date = '2026-10-15'`, '80 / 70 / 90');
await vale('Uso del reloj de María (minutos puesto en el día)', `SELECT coalesce(sum(minutos_puesto), 0) n FROM "SINCRONIZACION" WHERE id_asignacion = '${A_MARIA}'`, 110);
await vale('Hallazgo de la red de seguridad: SpO2 bajo 90 sin alerta cercana del reloj', `SELECT count(*) n FROM "REGISTRO_VITAL" r
  WHERE r.tipo = 'SPO2' AND r.valor < 90 AND NOT EXISTS (SELECT 1 FROM "ALERTA" al WHERE al.id_asignacion = r.id_asignacion AND al.origen = 'RELOJ'
    AND al.fecha_hora_deteccion BETWEEN r.fecha_hora_medicion - interval '30 minutes' AND r.fecha_hora_medicion + interval '30 minutes')`, 1);
await vale('Métrica de validación: SpO2 descartados por los relojes', `SELECT sum(cantidad) n FROM "EVENTO_DESCARTADO" WHERE tipo_evento = 'SPO2'`, 5);
await vale('Métricas: alertas reales (sin simulacros ni canceladas)', `SELECT count(*) n FROM "ALERTA" WHERE NOT es_simulacro AND fecha_hora_cancelacion IS NULL`, 7);
await vale('Panel: alertas reales abiertas', `SELECT count(*) n FROM "ALERTA_ESTADO" e JOIN "ALERTA" a USING (id_alerta)
  WHERE NOT a.es_simulacro AND e.estado_contacto <> 'CANCELADA' AND e.estado_atencion <> 'RESUELTA'`, 4);
await vale('Panel: alertas sin respuesta (prioridad máxima)', `SELECT count(*) n FROM "ALERTA_ESTADO" WHERE estado_contacto = 'SIN_RESPUESTA'`, 1);
await vale('Panel: casos en seguimiento', `SELECT count(*) n FROM "ALERTA_ESTADO" WHERE estado_atencion = 'EN_SEGUIMIENTO'`, 0);
await vale('Inventario: relojes vigentes con batería bajo 20 % en su último lote', `SELECT count(*) n FROM (SELECT DISTINCT ON (s.id_asignacion) s.nivel_bateria FROM "SINCRONIZACION" s
  JOIN "ASIGNACION_RELOJ" a USING (id_asignacion) WHERE a.fecha_fin IS NULL ORDER BY s.id_asignacion, s.fecha_hora_recepcion DESC) u WHERE nivel_bateria < 20`, 1);

// =====================================================================
nuevoGrupo('18. Auditoría de cambios');
await acepta('Ana modifica a María y cambia su configuración; queda en la auditoría', `INSERT INTO "REGISTRO_AUDITORIA"(id_auditoria, id_usuario, accion, entidad, id_entidad, detalle, fecha_hora) VALUES
  (gen_random_uuid(), '${ANA}', 'MODIFICAR', 'PACIENTE', '${MARIA}', '{"campo": "nivel_dependencia"}', now()),
  (gen_random_uuid(), '${ANA}', 'CREAR', 'CONFIGURACION', '${MARIA}|${T2}', null, now()),
  (gen_random_uuid(), '${ANA}', 'MODIFICAR', 'ROL', 'COORDINADOR', '{"permiso": "REPORTES/EXPORTAR"}', now())`);
await acepta('Auditar la desactivación de un contacto, la ficha, una condición y un permiso, cada uno con su propio id', `INSERT INTO "REGISTRO_AUDITORIA"(id_auditoria, id_usuario, accion, entidad, id_entidad, fecha_hora) VALUES
  (gen_random_uuid(), '${ANA}', 'MODIFICAR', 'CONTACTO_PACIENTE', '${P3}', now()),
  (gen_random_uuid(), '${ANA}', 'MODIFICAR', 'FICHA_PACIENTE', '${PEDRO}', now()),
  (gen_random_uuid(), '${ANA}', 'CREAR', 'CONDICION_PACIENTE', gen_random_uuid()::text, now()),
  (gen_random_uuid(), '${ADMIN}', 'ELIMINAR', 'PERMISO_ROL', 'PERSONAL|ALERTAS|EDITAR', now())`);
await vale('Historial de un objeto con clave compuesta (configuración de María del 10-10)', `SELECT count(*) n FROM "REGISTRO_AUDITORIA"
  WHERE entidad = 'CONFIGURACION' AND id_entidad = '${MARIA}|${T2}'`, 1);
await rechaza('Borrar a una profesional con historial (se desactiva, no se borra)', `DELETE FROM "USUARIO" WHERE id_usuario = '${ANA}'`, 'foranea');

// =====================================================================
nuevoGrupo('19. Borrado al terminar el piloto (4.4.5)');
await rechaza('Borrar un reloj asignado', `DELETE FROM "SMARTWATCH" WHERE id_dispositivo = '${W1}'`, 'foranea');
await rechaza('Borrar un vínculo que tiene avisos enviados', `DELETE FROM "CONTACTO_PACIENTE" WHERE id_contacto_paciente = '${M1}'`, 'foranea');
await rechaza('Borrar una persona que es contacto de pacientes', `DELETE FROM "CONTACTO" WHERE id_contacto = '${SEG_CIUDADANA}'`, 'foranea');
await acepta('Borrar a María con todos sus datos', `DELETE FROM "PACIENTE" WHERE id_paciente = '${MARIA}'`);
await vale('No queda ningún dato de María', `SELECT (SELECT count(*) FROM "FICHA_PACIENTE" WHERE id_paciente = '${MARIA}')
  + (SELECT count(*) FROM "CONDICION_PACIENTE" WHERE id_paciente = '${MARIA}') + (SELECT count(*) FROM "DOCUMENTO" WHERE id_paciente = '${MARIA}')
  + (SELECT count(*) FROM "CONTACTO_PACIENTE" WHERE id_paciente = '${MARIA}') + (SELECT count(*) FROM "CONFIGURACION" WHERE id_paciente = '${MARIA}')
  + (SELECT count(*) FROM "UMBRAL" WHERE id_paciente = '${MARIA}') + (SELECT count(*) FROM "ZONA_SEGURA" WHERE id_paciente = '${MARIA}')
  + (SELECT count(*) FROM "ASIGNACION_RELOJ" WHERE id_paciente = '${MARIA}')
  + (SELECT count(*) FROM "SINCRONIZACION" WHERE id_asignacion = '${A_MARIA}') + (SELECT count(*) FROM "REGISTRO_VITAL" WHERE id_asignacion = '${A_MARIA}')
  + (SELECT count(*) FROM "UBICACION" WHERE id_asignacion = '${A_MARIA}') + (SELECT count(*) FROM "EVENTO_DESCARTADO" WHERE id_sincronizacion = '${S_MARIA}')
  + (SELECT count(*) FROM "ALERTA" WHERE id_asignacion = '${A_MARIA}') + (SELECT count(*) FROM "ATENCION_ALERTA" WHERE id_alerta = '${CAIDA_MARIA}')
  + (SELECT count(*) FROM "REGISTRO_INTENTO" WHERE id_alerta = '${CAIDA_MARIA}') + (SELECT count(*) FROM "AVISO_FCM" WHERE id_alerta = '${CAIDA_MARIA}')
  + (SELECT count(*) FROM "SEGUIMIENTO" WHERE id_alerta = '${CAIDA_MARIA}') + (SELECT count(*) FROM "DISCREPANCIA_ALERTA" WHERE id_alerta = '${CAIDA_MARIA}') n`, 0);
await vale('Pedro y Rosa conservan sus datos (5 vínculos + 8 alertas)', `SELECT (SELECT count(*) FROM "CONTACTO_PACIENTE" WHERE id_paciente IN ('${PEDRO}', '${ROSA}'))
  + (SELECT count(*) FROM "ALERTA" a JOIN "ASIGNACION_RELOJ" r USING (id_asignacion) WHERE r.id_paciente IN ('${PEDRO}', '${ROSA}')) n`, 13);
await vale('La auditoría sobre María sobrevive al borrado', `SELECT count(*) n FROM "REGISTRO_AUDITORIA" WHERE id_entidad LIKE '${MARIA}%'`, 2);
await vale('Seguridad Ciudadana se conserva (sigue siendo contacto de Pedro y Rosa)', `SELECT count(*) n FROM "CONTACTO" WHERE id_contacto = '${SEG_CIUDADANA}'`, 1);
await acepta('Limpieza: el backend borra las personas que quedaron sin pacientes (no los contactos internos)', `DELETE FROM "CONTACTO" k WHERE k.id_usuario IS NULL AND NOT EXISTS (SELECT 1 FROM "CONTACTO_PACIENTE" cp WHERE cp.id_contacto = k.id_contacto)`);
await vale('Se borró solo la hija de María', `SELECT count(*) || ' / ' || count(*) FILTER (WHERE id_contacto = '${HIJA}') n FROM "CONTACTO" WHERE id_usuario IS NULL`, '3 / 0');
await vale('El contacto interno de Ana se conserva', `SELECT count(*) n FROM "CONTACTO" WHERE id_contacto = '${INTERNO_ANA}'`, 1);
await vale('Se conservan profesionales, relojes y documentos de otros pacientes', `SELECT (SELECT count(*) FROM "USUARIO") || ' / ' || (SELECT count(*) FROM "SMARTWATCH") n`, '4 / 5');

// ---------- Informe ----------
const ICONO = { ok: '✅', falla: '❌', hueco: '⚠️', backend: 'ℹ️' };
const cuenta = (lista, e) => lista.filter((r) => r.estado === e).length;
const total = resultados.length;
const fallas = cuenta(resultados, 'falla');
const fecha = new Date().toLocaleString('es-CL', { timeZone: 'America/Santiago', dateStyle: 'long', timeStyle: 'short' });
const celda = (t) => String(t).replace(/\|/g, '\\|').replace(/\s+/g, ' ');
const grupos = [...new Set(resultados.map((r) => r.grupo))];

let md = `# Pruebas del modelo de datos

Informe generado automáticamente por [\`pruebas-modelo-datos/prueba-modelo.mjs\`](pruebas-modelo-datos/prueba-modelo.mjs). No editar a mano: se reescribe en cada ejecución.

| | |
|---|---|
| Fecha | ${fecha} |
| Modelo | [\`modelo-datos.dbml\`](modelo-datos.dbml) + [\`reglas-extra.sql\`](pruebas-modelo-datos/reglas-extra.sql) |
| Base de datos | PostgreSQL embebido (PGlite ${versionDe('@electric-sql/pglite')}), conversión con @dbml/core ${versionDe('@dbml/core')}, Node ${process.version} |
| **Resultado** | **${total} casos: ${cuenta(resultados, 'ok')} ✅ · ${fallas} ❌ · ${cuenta(resultados, 'hueco')} ⚠️ · ${cuenta(resultados, 'backend')} ℹ️** |

## Cómo repetir las pruebas

\`\`\`bash
cd docs/pruebas-modelo-datos
npm install
npm test
\`\`\`

Cada ejecución crea la base desde cero en memoria, con datos inventados (pacientes María, Pedro y Rosa; profesionales Ana y Luis), y no toca ninguna base real. El grupo A usa una base aparte para revisar las relaciones uno a uno. Si hay casos ❌, el comando termina con error.

**Cómo leer los resultados:**
- ✅ **Se acepta / Se rechaza (motivo) / = valor:** la base hace lo que dicen las notas del modelo.
- ❌ La base no hace lo que dicen las notas del modelo: hay que corregirlo.
- ⚠️ **Hueco:** la base permite algo que no debería y las notas no lo cubren. Ver la recomendación al final.
- ℹ️ **Lo valida el backend:** la base lo permite a propósito; la nota del modelo dice que lo controla el backend.

## Resumen por grupo

| Grupo | Casos | ✅ | ❌ | ⚠️ | ℹ️ |
|---|---|---|---|---|---|
${grupos.map((g) => { const rs = resultados.filter((r) => r.grupo === g); return `| ${g} | ${rs.length} | ${cuenta(rs, 'ok')} | ${cuenta(rs, 'falla')} | ${cuenta(rs, 'hueco')} | ${cuenta(rs, 'backend')} |`; }).join('\n')}

## Detalle
`;
let n = 0;
for (const r of resultados) r.n = ++n;
for (const g of grupos) {
  md += `\n### ${g}\n\n| # | Caso | Esperado | Obtenido | |\n|---|---|---|---|---|\n`;
  for (const r of resultados.filter((x) => x.grupo === g)) {
    md += `| ${r.n} | ${celda(r.caso)} | ${celda(r.esperado)} | ${celda(r.obtenido)} | ${ICONO[r.estado]} |\n`;
  }
}
const conNota = resultados.filter((r) => r.estado === 'falla' || r.estado === 'hueco');
if (conNota.length) {
  md += `\n## Qué corregir\n\n| # | Caso | | Por qué / recomendación |\n|---|---|---|---|\n`;
  for (const r of conNota) md += `| ${r.n} | ${celda(r.caso)} | ${ICONO[r.estado]} | ${celda(r.nota || r.obtenido)} |\n`;
}
writeFileSync(join(aqui, '../pruebas-modelo-datos.md'), md);

for (const r of resultados.filter((x) => x.estado === 'falla')) console.log(`❌ [${r.grupo}] ${r.caso}: esperado ${r.esperado}, obtenido ${r.obtenido}`);
console.log(`${total} casos: ${cuenta(resultados, 'ok')} ✅ · ${fallas} ❌ · ${cuenta(resultados, 'hueco')} ⚠️ · ${cuenta(resultados, 'backend')} ℹ️. Informe: docs/pruebas-modelo-datos.md`);
process.exit(fallas === 0 ? 0 : 1);

function gen() { return '00000000-0000-0000-0000-' + String(Math.floor(Math.random() * 1e12)).padStart(12, '0'); }
