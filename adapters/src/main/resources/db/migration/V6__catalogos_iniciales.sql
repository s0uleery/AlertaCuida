-- Catálogos que el sistema necesita desde el primer día, también en producción.
-- Se pueden ampliar o desactivar desde el dashboard; no se borran mientras algo los use.

INSERT INTO rol (codigo, nombre, es_sistema) VALUES
	('ADMINISTRADOR', 'Administrador', true),
	('COORDINADOR', 'Coordinador', false),
	('PERSONAL', 'Personal', false);

-- El administrador tiene todos los permisos (no se editan, para no dejar a nadie sin acceso).
-- Los de los demás roles son los iniciales: el administrador los cambia desde el dashboard.
-- Coordinador: alertas, usuarios y reportes completos, sin administración. Personal: ver y editar alertas.
INSERT INTO permiso_rol (codigo_rol, modulo, accion)
SELECT r.codigo_rol, m.modulo, a.accion
FROM (VALUES ('ADMINISTRADOR'), ('COORDINADOR')) AS r (codigo_rol)
CROSS JOIN (VALUES ('ALERTAS'), ('USUARIOS'), ('REPORTES'), ('ADMINISTRACION')) AS m (modulo)
CROSS JOIN (VALUES ('VER'), ('CREAR'), ('EDITAR'), ('ELIMINAR'), ('EXPORTAR')) AS a (accion)
WHERE r.codigo_rol = 'ADMINISTRADOR' OR m.modulo <> 'ADMINISTRACION';

INSERT INTO permiso_rol (codigo_rol, modulo, accion) VALUES
	('PERSONAL', 'ALERTAS', 'VER'),
	('PERSONAL', 'ALERTAS', 'EDITAR');

INSERT INTO tipo_contacto (codigo, nombre) VALUES
	('FAMILIA', 'Familia'),
	('VECINO', 'Vecino o vecina'),
	('PRLAC', 'Equipo PRLAC'),
	('SEGURIDAD_CIUDADANA', 'Seguridad Ciudadana'),
	('INSTITUCION', 'Institución'),
	('OTRO', 'Otro');

INSERT INTO destino_derivacion (codigo, nombre) VALUES
	('CESFAM', 'CESFAM'),
	('SAMU', 'SAMU'),
	('HOSPITAL', 'Hospital'),
	('OTRO', 'Otro');
