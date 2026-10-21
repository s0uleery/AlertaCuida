-- Relojes, asignaciones y datos recibidos del reloj (tareas 1.1.2 y 1.1.3).

-- Para impedir asignaciones superpuestas en el tiempo (EXCLUDE con uuid)
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE smartwatch (
	id_dispositivo uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	identificador_reloj varchar NOT NULL UNIQUE,  -- ID único que informa el reloj
	modelo varchar,
	iccid varchar UNIQUE,                       -- identificador físico de la SIM LTE
	numero_sim varchar UNIQUE,                  -- puede cambiar, el ICCID no
	fecha_vencimiento_plan date,                -- vencido = el paciente queda sin alertas (3.8.2)
	token_fcm varchar,
	fecha_token_fcm timestamptz,
	-- Estado físico. Si está asignado se sabe por asignacion_reloj
	estado varchar NOT NULL CHECK (estado IN ('OPERATIVO', 'EN_REPARACION', 'RETIRADO'))
);

-- Vínculo reloj-paciente con historial (1.2.4). Todo lo que llega del reloj cuelga de aquí.
-- Cerrar la asignación revoca el token. Fecha_fin vacía = rango abierto; cerrar a las 12:00
-- y abrir otra a las 12:00 no se superpone.
-- Lo valida el backend: solo relojes OPERATIVO, a pacientes con consentimiento y sin baja.
CREATE TABLE asignacion_reloj (
	id_asignacion uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_dispositivo uuid NOT NULL REFERENCES smartwatch (id_dispositivo),
	id_paciente uuid NOT NULL REFERENCES paciente (id_paciente) ON DELETE CASCADE,
	fecha_inicio timestamptz NOT NULL,
	fecha_fin timestamptz,
	hash_token_reloj varchar NOT NULL UNIQUE,   -- token del reloj (rol DEVICE)
	CONSTRAINT asignacion_fechas CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio),
	-- Sin superposiciones, ni vigentes ni cerradas, por reloj y por paciente (1.2.5)
	CONSTRAINT asignacion_reloj_sin_cruce
		EXCLUDE USING gist (id_dispositivo WITH =, tstzrange(fecha_inicio, fecha_fin) WITH &&),
	CONSTRAINT asignacion_paciente_sin_cruce
		EXCLUDE USING gist (id_paciente WITH =, tstzrange(fecha_inicio, fecha_fin) WITH &&)
);

-- Un lote cada 2 h, o antes si la batería baja de 15 %. La última fila de cada reloj da su estado actual.
-- Configuración pendiente: hash_configuracion del último lote distinto del que calcula el backend.
CREATE TABLE sincronizacion (
	id_sincronizacion uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	event_id uuid NOT NULL UNIQUE,              -- evita duplicados si el reloj reintenta
	id_asignacion uuid NOT NULL REFERENCES asignacion_reloj (id_asignacion) ON DELETE CASCADE,
	hash_configuracion varchar,
	fecha_hora_envio timestamptz,               -- hora del reloj
	fecha_hora_recepcion timestamptz NOT NULL,
	nivel_bateria int,
	reloj_puesto boolean,
	minutos_puesto int,                         -- desde el lote anterior (uso del reloj en reportes)
	tipo_conexion varchar CHECK (tipo_conexion IN ('LTE', 'WIFI')),
	version_app varchar,
	-- Destino de las claves compuestas de registro_vital y ubicacion
	UNIQUE (id_sincronizacion, id_asignacion),
	CONSTRAINT sincronizacion_bateria CHECK (nivel_bateria BETWEEN 0 AND 100),
	CONSTRAINT sincronizacion_minutos_puesto CHECK (minutos_puesto >= 0)
);

CREATE INDEX sincronizacion_asignacion ON sincronizacion (id_asignacion, fecha_hora_recepcion);

-- Contadores del lote, uno por tipo de evento (métricas de validación, 4.7)
CREATE TABLE evento_descartado (
	id_sincronizacion uuid NOT NULL REFERENCES sincronizacion (id_sincronizacion) ON DELETE CASCADE,
	tipo_evento varchar NOT NULL CHECK (tipo_evento IN ('CAIDA', 'FC', 'SPO2', 'FC_SPO2', 'TENDENCIA',
		'INACTIVIDAD', 'BATERIA_BAJA', 'RELOJ_NO_PUESTO')),  -- SILENCIO_DATOS lo detecta el servidor
	cantidad int NOT NULL,
	PRIMARY KEY (id_sincronizacion, tipo_evento),
	CONSTRAINT evento_descartado_cantidad CHECK (cantidad > 0)
);

-- Lecturas de FC y SpO2 (cada 30 min). El reloj guarda 14 días y puede reenviar historial.
-- id_asignacion es redundancia controlada: la clave compuesta obliga a que sea la del lote,
-- y permite el único que evita duplicar una lectura reenviada en otro lote.
CREATE TABLE registro_vital (
	id_registro uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_sincronizacion uuid NOT NULL,
	id_asignacion uuid NOT NULL,
	tipo varchar NOT NULL CHECK (tipo IN ('FC', 'SPO2')),
	valor numeric NOT NULL,
	fecha_hora_medicion timestamptz NOT NULL,   -- hora real de la medición en el reloj
	UNIQUE (id_asignacion, tipo, fecha_hora_medicion),
	FOREIGN KEY (id_sincronizacion, id_asignacion)
		REFERENCES sincronizacion (id_sincronizacion, id_asignacion) ON DELETE CASCADE
);

-- Borrado en cascada desde el lote (es la tabla más grande)
CREATE INDEX registro_vital_sincronizacion ON registro_vital (id_sincronizacion);

-- Solo para pacientes con GPS activo. Muestra dónde está la persona; no genera alertas.
CREATE TABLE ubicacion (
	id_ubicacion uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	id_sincronizacion uuid NOT NULL,
	id_asignacion uuid NOT NULL,                -- redundancia controlada, como en registro_vital
	latitud numeric NOT NULL,
	longitud numeric NOT NULL,
	precision_metros numeric,
	fecha_hora_medicion timestamptz NOT NULL,
	UNIQUE (id_asignacion, fecha_hora_medicion),
	FOREIGN KEY (id_sincronizacion, id_asignacion)
		REFERENCES sincronizacion (id_sincronizacion, id_asignacion) ON DELETE CASCADE
);

CREATE INDEX ubicacion_sincronizacion ON ubicacion (id_sincronizacion);
