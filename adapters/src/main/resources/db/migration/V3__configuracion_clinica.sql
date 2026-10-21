-- Configuración clínica y umbrales, cada uno con su historial (tarea 1.1.4).
-- Cada cambio agrega una fila; la vigente es la de mayor vigente_desde. Quién cambió qué queda en la auditoría.

-- Un módulo por columna: son pocos y fijos (sin jsonb). La regla de tendencia la define el backend.
CREATE TABLE configuracion (
	id_paciente uuid NOT NULL REFERENCES paciente (id_paciente) ON DELETE CASCADE,
	vigente_desde timestamptz NOT NULL,
	caidas_activa boolean NOT NULL DEFAULT true,
	fc_activa boolean NOT NULL DEFAULT true,
	spo2_activa boolean NOT NULL DEFAULT true,
	inactividad_activa boolean NOT NULL DEFAULT true,
	tendencia_activa boolean NOT NULL DEFAULT false,
	gps_activo boolean NOT NULL DEFAULT false,  -- solo para ubicar a la persona; no genera alertas
	modo_conservador boolean NOT NULL DEFAULT false,
	minutos_espera int,                         -- antes de pasar al siguiente contacto. Vacío = valor base
	motivo_cambio text,                         -- cifrado
	PRIMARY KEY (id_paciente, vigente_desde),
	CONSTRAINT configuracion_espera CHECK (minutos_espera > 0)
);

-- Rangos personalizados por variable: grave_bajo < normal_min < normal_max < grave_alto.
-- Una fila va completa o vacía: vacía (o sin fila) = rige el umbral base del backend.
-- SpO2 no tiene límite alto: normal_max y grave_alto van siempre vacíos.
CREATE TABLE umbral (
	id_paciente uuid NOT NULL REFERENCES paciente (id_paciente) ON DELETE CASCADE,
	variable varchar NOT NULL CHECK (variable IN ('FC', 'SPO2')),
	vigente_desde timestamptz NOT NULL,
	grave_bajo numeric,
	normal_min numeric,
	normal_max numeric,
	grave_alto numeric,
	PRIMARY KEY (id_paciente, variable, vigente_desde),
	CONSTRAINT umbral_completo CHECK (
		(grave_bajo IS NULL AND normal_min IS NULL AND normal_max IS NULL AND grave_alto IS NULL)
		OR (grave_bajo IS NOT NULL AND normal_min IS NOT NULL AND (
			(variable = 'SPO2' AND normal_max IS NULL AND grave_alto IS NULL)
			OR (variable <> 'SPO2' AND normal_max IS NOT NULL AND grave_alto IS NOT NULL)))),
	CONSTRAINT umbral_orden CHECK (grave_bajo IS NULL OR (grave_bajo < normal_min
		AND (normal_max IS NULL OR (normal_min < normal_max AND normal_max < grave_alto)))),
	CONSTRAINT umbral_spo2 CHECK (variable <> 'SPO2' OR normal_min IS NULL OR normal_min <= 100)
);
