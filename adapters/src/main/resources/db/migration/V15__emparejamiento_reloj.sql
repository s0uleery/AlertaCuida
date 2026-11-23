-- Emparejamiento del reloj con un código corto (como en los Smart TV). El reloj, sin token, pide un código y recibe
-- también el token que usará; muestra el código en pantalla y quien instala lo escribe en el dashboard al vincular.
-- Al vincular, el hash del token pasa a asignacion_reloj y la fila se borra: cada código sirve una sola vez.
-- Las filas vencidas se borran cada vez que un reloj pide un código nuevo.
CREATE TABLE emparejamiento_reloj (
	id_emparejamiento uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	hash_codigo varchar NOT NULL UNIQUE,        -- SHA-256 del código normalizado (sin guion, en mayúsculas)
	hash_token varchar NOT NULL UNIQUE,         -- SHA-256 del token que ya tiene el reloj
	fecha_creacion timestamptz NOT NULL,
	fecha_expiracion timestamptz NOT NULL,
	CONSTRAINT emparejamiento_reloj_vigencia CHECK (fecha_expiracion > fecha_creacion)
);
