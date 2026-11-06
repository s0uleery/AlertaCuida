-- Discrepancias de las alertas del reloj, inmovilidad del agravamiento, alerta técnica por exceso de alertas
-- y avisos por correo apagados por defecto.

-- Las cuentas nuevas no reciben correos de alertas hasta que alguien lo encienda; las que ya existen no cambian.
ALTER TABLE usuario ALTER COLUMN recibe_avisos SET DEFAULT false;

-- Lo que no calzó entre la alerta y la configuración vigente al recibirla. La alerta se acepta igual: esto
-- solo avisa al equipo. Se guarda al recibirla porque no se puede recalcular después: los valores base del
-- backend (application.properties) no tienen historial. Una fila por discrepancia (1FN).
CREATE TABLE discrepancia_alerta (
	id_alerta uuid NOT NULL REFERENCES alerta (id_alerta) ON DELETE CASCADE,
	codigo varchar NOT NULL CHECK (codigo IN ('VERSION_DISTINTA', 'MODULO_APAGADO', 'GRAVEDAD_DISTINTA',
		'LECTURAS_EN_RANGO')),
	detalle text NOT NULL,                      -- legible, ej. "FC 95 dentro del rango normal 50–110"
	PRIMARY KEY (id_alerta, codigo)
);

-- Cuánto tiempo quedó la persona sin moverse cuando la caída pasó a grave. Solo con agravamiento.
ALTER TABLE alerta
	ADD COLUMN segundos_inmovilidad_agravamiento int,
	ADD CONSTRAINT alerta_inmovilidad_positiva CHECK (segundos_inmovilidad_agravamiento >= 0),
	ADD CONSTRAINT alerta_inmovilidad_agravamiento
		CHECK (segundos_inmovilidad_agravamiento IS NULL OR fecha_hora_agravamiento IS NOT NULL);

-- EXCESO_ALERTAS: técnica del servidor, cuando un reloj envía demasiadas alertas en poco tiempo (posible falla).
ALTER TABLE alerta DROP CONSTRAINT alerta_tipo_evento_check;
ALTER TABLE alerta ADD CONSTRAINT alerta_tipo_evento_check
	CHECK (tipo_evento IN ('CAIDA', 'FC', 'SPO2', 'FC_SPO2', 'TENDENCIA', 'INACTIVIDAD', 'ZONA_SEGURA',
		'BOTON_AYUDA', 'SILENCIO_DATOS', 'BATERIA_BAJA', 'RELOJ_NO_PUESTO', 'EXCESO_ALERTAS'));
ALTER TABLE alerta DROP CONSTRAINT alerta_servidor_tecnica;
ALTER TABLE alerta ADD CONSTRAINT alerta_servidor_tecnica CHECK (origen = 'RELOJ' OR es_simulacro
	OR tipo_evento IN ('SILENCIO_DATOS', 'BATERIA_BAJA', 'RELOJ_NO_PUESTO', 'EXCESO_ALERTAS'));
-- Como el silencio de datos, el exceso de alertas solo lo detecta el servidor
ALTER TABLE alerta ADD CONSTRAINT alerta_exceso_servidor CHECK (tipo_evento <> 'EXCESO_ALERTAS' OR origen = 'SERVIDOR');
