-- Deja constancia de un token de renovación reutilizado (posible robo): cierra las sesiones de la cuenta.
-- Es una acción propia porque LOGIN_FALLIDO cuenta para el bloqueo de la cuenta.
ALTER TABLE registro_auditoria DROP CONSTRAINT registro_auditoria_accion_check;
ALTER TABLE registro_auditoria ADD CONSTRAINT registro_auditoria_accion_check
	CHECK (accion IN ('LEER', 'CREAR', 'MODIFICAR', 'ELIMINAR', 'EXPORTAR', 'LOGIN', 'LOGIN_FALLIDO',
		'TOKEN_REUTILIZADO'));
