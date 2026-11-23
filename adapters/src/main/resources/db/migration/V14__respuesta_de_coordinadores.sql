-- Revisión del flujo de alertas (16-11-2026): en las alertas de salud, cada coordinador recibe el aviso a la
-- oficina (y el de agravamiento) por correo y WhatsApp, y puede confirmar que va. Su "Voy en camino" confirma la
-- alerta y detiene al reloj, igual que el de un contacto del paciente. Por eso la respuesta ya no es solo de un
-- vínculo: también del aviso a la oficina y del de agravamiento. El correo del vigilante no pide confirmar.
-- Que sea solo en las alertas de salud depende de la tabla alerta y lo controla el backend.
ALTER TABLE registro_intento
	DROP CONSTRAINT intento_respuesta_externo,
	ADD CONSTRAINT intento_respuesta_externo CHECK (respuesta IS NULL OR id_contacto_paciente IS NOT NULL
		OR proposito IN ('AVISO', 'AGRAVAMIENTO'));
