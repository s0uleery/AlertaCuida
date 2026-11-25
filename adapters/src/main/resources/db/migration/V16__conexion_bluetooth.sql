-- El reloj también puede sincronizar a través del teléfono emparejado por Bluetooth (pedido de quien desarrolla
-- la app del reloj, 24-11-2026). Se amplía el CHECK de la conexión informada en cada lote.
ALTER TABLE sincronizacion DROP CONSTRAINT sincronizacion_tipo_conexion_check;
ALTER TABLE sincronizacion ADD CONSTRAINT sincronizacion_tipo_conexion_check
	CHECK (tipo_conexion IN ('LTE', 'WIFI', 'BLUETOOTH'));
