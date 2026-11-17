-- Aviso a la oficina PRLAC (2.10.5): cada alerta se avisa a los coordinadores y, si pasa a grave después de ese
-- aviso, se les avisa de nuevo. El segundo aviso es otro propósito para que la clave única de los avisos al equipo
-- (alerta, contacto interno, canal y propósito) lo permita una sola vez. Como el del vigilante, solo va al equipo.
ALTER TABLE registro_intento
	DROP CONSTRAINT registro_intento_proposito_check,
	ADD CONSTRAINT registro_intento_proposito_check CHECK (proposito IN ('AVISO', 'VIGILANTE', 'AGRAVAMIENTO'));
