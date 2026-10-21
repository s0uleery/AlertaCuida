-- Pacientes (identidad), datos de salud, documentos y contactos (tarea 1.1.1).
-- Identidad y salud en tablas distintas (0.4.2).

-- Solo identidad. Activo = sin fecha_baja; la edad la calcula el backend.
-- Lo valida el backend: sin fecha_consentimiento no se le asigna reloj; al darlo de baja se cierra su asignación.
CREATE TABLE paciente (
	id_paciente uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	rut varchar NOT NULL,                       -- cifrado
	rut_hash varchar NOT NULL UNIQUE,           -- buscar y evitar duplicados sin descifrar
	nombres varchar,                            -- cifrado
	apellidos varchar,                          -- cifrado
	alias varchar,                              -- nombre corto para los mensajes, sin datos clínicos
	fecha_nacimiento varchar,                   -- cifrado (date antes de cifrar)
	sexo varchar CHECK (sexo IN ('FEMENINO', 'MASCULINO')),
	direccion varchar,                          -- cifrado
	nivel_dependencia varchar CHECK (nivel_dependencia IN ('MODERADA', 'SEVERA')),  -- vacío = por evaluar
	fecha_consentimiento date,                  -- consentimiento informado firmado (5.5.1)
	fecha_ingreso date NOT NULL,
	fecha_baja date,
	CONSTRAINT paciente_fechas CHECK (fecha_baja IS NULL OR fecha_baja >= fecha_ingreso)
);

-- Uno a uno con paciente: datos de salud y sociales que no cambian la configuración del reloj
CREATE TABLE ficha_paciente (
	id_paciente uuid PRIMARY KEY REFERENCES paciente (id_paciente) ON DELETE CASCADE,
	registro_discapacidad boolean NOT NULL DEFAULT false,  -- inscrito en el Registro Nacional de la Discapacidad
	notas text                                  -- cifrado
);

-- Enfermedades y situaciones (discapacidad, movilidad reducida...): mismas columnas, se distinguen por tipo
CREATE TABLE condicion_paciente (
	id_condicion uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_paciente uuid NOT NULL REFERENCES paciente (id_paciente) ON DELETE CASCADE,
	tipo varchar NOT NULL CHECK (tipo IN ('ENFERMEDAD', 'SITUACION')),
	descripcion varchar NOT NULL                -- cifrado
);

-- El archivo va cifrado en el disco del servidor; aquí solo la ruta y su hash
CREATE TABLE documento (
	id_documento uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_paciente uuid NOT NULL REFERENCES paciente (id_paciente) ON DELETE CASCADE,
	tipo varchar NOT NULL CHECK (tipo IN ('INFORME_SOCIOSANITARIO', 'CONSENTIMIENTO', 'OTRO')),
	nombre_archivo varchar NOT NULL,
	ruta varchar NOT NULL UNIQUE,
	hash_sha256 varchar NOT NULL,               -- verifica que el archivo no fue alterado
	fecha_subida timestamptz NOT NULL,
	id_usuario uuid NOT NULL REFERENCES usuario (id_usuario)  -- quién lo subió
);

-- Quién es la persona respecto del paciente. Ser cuidador es un rol del vínculo, no un tipo.
CREATE TABLE tipo_contacto (
	codigo varchar PRIMARY KEY,
	nombre varchar NOT NULL,
	activo boolean NOT NULL DEFAULT true
);

-- La persona, una sola vez aunque sea contacto de varios pacientes. Solo nombre y medio de contacto.
-- El backend normaliza número y correo antes de calcular el hash, y al terminar el piloto borra
-- los contactos que quedan sin pacientes.
CREATE TABLE contacto (
	id_contacto uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	nombre varchar NOT NULL,                    -- cifrado. Viaja al reloj ("Alerta recibida por...")
	whatsapp varchar,                           -- cifrado
	whatsapp_hash varchar UNIQUE,               -- un número es una sola persona
	fecha_opt_in timestamptz,                   -- Meta lo registra por número: es de la persona
	fecha_revocacion_opt_in timestamptz,
	correo varchar,                             -- cifrado
	correo_hash varchar UNIQUE,                 -- un correo es una sola persona
	CONSTRAINT contacto_canal CHECK (whatsapp IS NOT NULL OR correo IS NOT NULL),
	CONSTRAINT contacto_whatsapp_hash CHECK ((whatsapp IS NULL) = (whatsapp_hash IS NULL)),
	CONSTRAINT contacto_correo_hash CHECK ((correo IS NULL) = (correo_hash IS NULL)),
	CONSTRAINT contacto_opt_in CHECK (fecha_revocacion_opt_in IS NULL OR fecha_opt_in IS NOT NULL)
);

-- La lista de contactos de cada paciente. Viaja al reloj sin número ni correo.
-- Si hora_inicio > hora_fin, el horario cruza la medianoche; vacío = todo el día.
CREATE TABLE contacto_paciente (
	id_contacto_paciente uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_paciente uuid NOT NULL REFERENCES paciente (id_paciente) ON DELETE CASCADE,
	id_contacto uuid NOT NULL REFERENCES contacto (id_contacto),
	codigo_tipo_contacto varchar NOT NULL REFERENCES tipo_contacto (codigo),
	parentesco varchar,                         -- ej. hija, sobrino. Solo si el tipo es FAMILIA
	es_cuidador_principal boolean NOT NULL DEFAULT false,
	orden_prioridad int NOT NULL,
	hora_inicio time,
	hora_fin time,
	activo boolean NOT NULL DEFAULT true,       -- se desactiva, no se borra: sus intentos quedan
	UNIQUE (id_paciente, id_contacto),
	CONSTRAINT contacto_paciente_horario CHECK ((hora_inicio IS NULL) = (hora_fin IS NULL)),
	CONSTRAINT contacto_paciente_horario_distinto CHECK (hora_inicio IS NULL OR hora_inicio <> hora_fin),
	CONSTRAINT contacto_paciente_prioridad_positiva CHECK (orden_prioridad >= 1),
	CONSTRAINT contacto_paciente_parentesco CHECK (parentesco IS NULL OR codigo_tipo_contacto = 'FAMILIA')
);

CREATE UNIQUE INDEX contacto_paciente_prioridad ON contacto_paciente (id_paciente, orden_prioridad) WHERE activo;
CREATE UNIQUE INDEX contacto_paciente_cuidador ON contacto_paciente (id_paciente)
	WHERE es_cuidador_principal AND activo;
-- De qué pacientes es contacto una persona; limpieza de contactos sin pacientes
CREATE INDEX contacto_paciente_contacto ON contacto_paciente (id_contacto);
