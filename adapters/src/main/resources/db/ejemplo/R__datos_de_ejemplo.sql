-- Datos de ejemplo para desarrollo local (1.1.6). Solo los carga el perfil dev (application-dev.properties).
-- Repetible: Flyway lo vuelve a correr si cambia, así que cada INSERT debe poder repetirse sin error.

-- Administrador de prueba. Inicia sesión con RUN 11.111.111-1 y contraseña Admin1234!
--   rut_hash: HMAC-SHA256 de "11111111-1" con la clave-run de application-dev.properties.
--   hash_contrasena: BCrypt (costo 10) de "Admin1234!".
--   rut: "11111111-1" cifrado con la clave-cifrado de application-dev.properties (lo revisa CifradoCamposTest).
INSERT INTO usuario (id_usuario, codigo_rol, rut, rut_hash, nombre_profesional, cargo, correo,
	hash_contrasena, activo)
VALUES ('00000000-0000-0000-0000-000000000001', 'ADMINISTRADOR',
	'v1:9RDK5ztxjd10uEbkHMPfeH3Q4wKkK0qPVewUS8V1xBbKNaQmTxM=',
	'dcc3a0623bf1338533030565b3f87bc2b84c196a320fe8293663fa100762fa2a', 'Administrador de prueba',
	'Desarrollo', 'admin@alertacuida.local', '$2a$10$7wHn7U4IhUCGUR72AVJc3.rtzncWtNpHnrxKy.5Sh/8vCUoxfxwlW', true)
ON CONFLICT (id_usuario) DO NOTHING;
