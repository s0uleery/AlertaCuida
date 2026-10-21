-- Personal del PRLAC, roles, permisos, sesiones y auditoría (tareas 1.1.5, 1.3, 1.5.2, 1.6).
-- Va primero porque DOCUMENTO, ATENCION_ALERTA y SEGUIMIENTO apuntan a usuario.
-- Modelo y razones: docs/modelo-datos.md. Los enums del modelo van como varchar con CHECK.

CREATE TABLE rol (
	codigo varchar PRIMARY KEY,
	nombre varchar NOT NULL,
	-- ADMINISTRADOR: sus permisos no se editan ni se borra (lo valida el backend)
	es_sistema boolean NOT NULL DEFAULT false,
	activo boolean NOT NULL DEFAULT true
);

-- La fila existe = el rol tiene ese permiso
CREATE TABLE permiso_rol (
	codigo_rol varchar NOT NULL REFERENCES rol (codigo) ON DELETE CASCADE,
	modulo varchar NOT NULL CHECK (modulo IN ('ALERTAS', 'USUARIOS', 'REPORTES', 'ADMINISTRACION')),
	accion varchar NOT NULL CHECK (accion IN ('VER', 'CREAR', 'EDITAR', 'ELIMINAR', 'EXPORTAR')),
	PRIMARY KEY (codigo_rol, modulo, accion)
);

CREATE TABLE usuario (
	id_usuario uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	codigo_rol varchar NOT NULL REFERENCES rol (codigo),
	rut varchar NOT NULL,                       -- cifrado
	rut_hash varchar NOT NULL UNIQUE,           -- inicio de sesión con RUN sin descifrar
	nombre_profesional varchar NOT NULL,
	cargo varchar,
	correo varchar NOT NULL UNIQUE,             -- enlaces de activación y recuperación
	telefono varchar,                           -- cifrado
	hash_contrasena varchar,                    -- vacío hasta que activa su cuenta
	totp_secreto varchar,                       -- cifrado
	totp_activo boolean NOT NULL DEFAULT false,
	recibe_avisos boolean NOT NULL DEFAULT true,
	recibe_resumen_diario boolean NOT NULL DEFAULT false,
	-- Inactivo sin contraseña = pendiente de activar; inactivo con contraseña = desactivado
	activo boolean NOT NULL DEFAULT false,
	CONSTRAINT usuario_activo CHECK (NOT activo OR hash_contrasena IS NOT NULL),
	-- Así el único no distingue mayúsculas
	CONSTRAINT usuario_correo_minusculas CHECK (correo = lower(correo))
);

CREATE TABLE codigo_respaldo (
	id_codigo uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_usuario uuid NOT NULL REFERENCES usuario (id_usuario) ON DELETE CASCADE,
	hash_codigo varchar NOT NULL,
	fecha_uso timestamptz,                      -- cada código sirve una sola vez
	UNIQUE (id_usuario, hash_codigo)
);

-- Token de renovación del dashboard (1.3.4)
CREATE TABLE token_sesion (
	id_token uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_usuario uuid NOT NULL REFERENCES usuario (id_usuario) ON DELETE CASCADE,
	hash_token varchar NOT NULL UNIQUE,
	fecha_emision timestamptz NOT NULL,
	fecha_expiracion timestamptz NOT NULL,
	fecha_revocacion timestamptz,
	CONSTRAINT token_sesion_vigencia CHECK (fecha_expiracion > fecha_emision)
);

-- Enlaces de activación y recuperación enviados por correo, de un solo uso
CREATE TABLE token_accion (
	id_token uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_usuario uuid NOT NULL REFERENCES usuario (id_usuario) ON DELETE CASCADE,
	tipo varchar NOT NULL CHECK (tipo IN ('ACTIVACION', 'RECUPERACION')),
	hash_token varchar NOT NULL UNIQUE,
	fecha_emision timestamptz NOT NULL,
	fecha_expiracion timestamptz NOT NULL,
	fecha_uso timestamptz,
	CONSTRAINT token_accion_vigencia CHECK (fecha_expiracion > fecha_emision)
);

-- Quién leyó o cambió qué y cuándo, sin datos personales (1.6, 1.5.2).
-- id_entidad sin clave foránea a propósito: el registro sobrevive al borrado de lo auditado.
-- En las claves compuestas une sus partes con "|" (ej. CONFIGURACION "id_paciente|vigente_desde").
CREATE TABLE registro_auditoria (
	id_auditoria uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_usuario uuid REFERENCES usuario (id_usuario),  -- vacío en un login fallido de un RUN inexistente
	accion varchar NOT NULL
		CHECK (accion IN ('LEER', 'CREAR', 'MODIFICAR', 'ELIMINAR', 'EXPORTAR', 'LOGIN', 'LOGIN_FALLIDO')),
	entidad varchar CHECK (entidad IN ('PACIENTE', 'FICHA_PACIENTE', 'CONDICION_PACIENTE', 'CONTACTO',
		'CONTACTO_PACIENTE', 'CONFIGURACION', 'UMBRAL', 'DOCUMENTO', 'SMARTWATCH', 'ASIGNACION_RELOJ', 'ALERTA',
		'ATENCION_ALERTA', 'SEGUIMIENTO', 'USUARIO', 'ROL', 'PERMISO_ROL', 'REPORTE')),
	id_entidad varchar,
	detalle jsonb,                              -- ej. valor anterior y nuevo
	fecha_hora timestamptz NOT NULL,
	ip varchar,
	CONSTRAINT auditoria_entidad CHECK ((entidad IS NULL) = (id_entidad IS NULL))
);

-- Último acceso y bloqueo por intentos fallidos
CREATE INDEX registro_auditoria_usuario ON registro_auditoria (id_usuario, accion, fecha_hora);
-- Historial de un objeto
CREATE INDEX registro_auditoria_entidad ON registro_auditoria (entidad, id_entidad, fecha_hora);
