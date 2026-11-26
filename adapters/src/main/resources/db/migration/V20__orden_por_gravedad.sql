-- Orden de los contactos por gravedad y tipos de contacto nuevos (decisiones del 26-11-2026).

-- Cada gravedad tiene su propia lista ordenada, y un contacto puede estar en varias con un lugar distinto en
-- cada una (por ejemplo, en las graves: 1 Carabineros, 2 Seguridad Ciudadana, 3 el tercero significativo).
-- Una alerta recorre solo la lista de su gravedad. Solo los contactos activos tienen filas aquí.
-- No lleva id_paciente (se sabe por el vínculo): que un lugar no se repita dentro de un paciente y gravedad, y
-- que ninguna gravedad quede vacía, lo revisa la aplicación.
CREATE TABLE orden_contacto (
	id_contacto_paciente uuid NOT NULL REFERENCES contacto_paciente (id_contacto_paciente) ON DELETE CASCADE,
	severidad varchar NOT NULL CHECK (severidad IN ('LEVE', 'MEDIA', 'GRAVE')),
	orden int NOT NULL CONSTRAINT orden_contacto_positivo CHECK (orden >= 1),   -- 1 = primero
	PRIMARY KEY (id_contacto_paciente, severidad)
);

-- Los contactos activos quedan en las tres gravedades con el orden que tenían
INSERT INTO orden_contacto (id_contacto_paciente, severidad, orden)
SELECT cp.id_contacto_paciente, g.severidad, cp.orden_prioridad
FROM contacto_paciente cp CROSS JOIN (VALUES ('LEVE'), ('MEDIA'), ('GRAVE')) AS g (severidad)
WHERE cp.activo;

DROP INDEX contacto_paciente_prioridad;
ALTER TABLE contacto_paciente
	DROP CONSTRAINT contacto_paciente_prioridad_positiva,
	DROP COLUMN orden_prioridad;

-- Familia y Vecino pasan a ser "Tercero significativo"; se agregan Carabineros y CESFAM. El parentesco se
-- conserva y ahora es del tercero significativo.
INSERT INTO tipo_contacto (codigo, nombre) VALUES
	('TERCERO_SIGNIFICATIVO', 'Tercero significativo'),
	('CARABINEROS', 'Carabineros'),
	('CESFAM', 'CESFAM');

ALTER TABLE contacto_paciente DROP CONSTRAINT contacto_paciente_parentesco;
UPDATE contacto_paciente SET codigo_tipo_contacto = 'TERCERO_SIGNIFICATIVO'
WHERE codigo_tipo_contacto IN ('FAMILIA', 'VECINO');
DELETE FROM tipo_contacto WHERE codigo IN ('FAMILIA', 'VECINO');
ALTER TABLE contacto_paciente ADD CONSTRAINT contacto_paciente_parentesco
	CHECK (parentesco IS NULL OR codigo_tipo_contacto = 'TERCERO_SIGNIFICATIVO');
