-- Horario hábil del PRLAC editable desde el dashboard (25-11-2026). Antes solo estaba en calendario.yml; ahora
-- vive aquí y calendario.yml solo lo siembra la primera vez que arranca el backend con la tabla vacía. Los días que
-- no aparecen no son hábiles. Los feriados siguen en calendario.yml.
CREATE TABLE horario_habil (
	dia_semana varchar PRIMARY KEY
		CHECK (dia_semana IN ('LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO')),
	inicio time NOT NULL,
	fin time NOT NULL,
	CONSTRAINT horario_habil_orden CHECK (inicio < fin)
);

-- El cambio de horario queda en la auditoría como MODIFICAR CALENDARIO
ALTER TABLE registro_auditoria DROP CONSTRAINT registro_auditoria_entidad_check;
ALTER TABLE registro_auditoria ADD CONSTRAINT registro_auditoria_entidad_check
	CHECK (entidad IN ('PACIENTE', 'FICHA_PACIENTE', 'CONDICION_PACIENTE', 'CONTACTO', 'CONTACTO_PACIENTE',
		'CONFIGURACION', 'UMBRAL', 'ZONA_SEGURA', 'DOCUMENTO', 'SMARTWATCH', 'ASIGNACION_RELOJ', 'ALERTA',
		'ATENCION_ALERTA', 'SEGUIMIENTO', 'USUARIO', 'ROL', 'PERMISO_ROL', 'REPORTE', 'CALENDARIO'));
