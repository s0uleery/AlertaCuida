package cl.ucn.alertacuida.application.alerta.modelo;

/** Cada cosa que puede aparecer en la línea de tiempo de una alerta. */
public enum TipoPaso {
	ALERTA_RECIBIDA,
	ALERTA_AGRAVADA,
	ALERTA_CANCELADA,
	/** El reloj pasó a otro contacto de la lista. */
	SALTO_CONTACTO,
	AVISO_ENVIADO,
	AVISO_ENTREGADO,
	AVISO_LEIDO,
	AVISO_FALLIDO,
	RESPUESTA_CONTACTO,
	/** El reloj recorrió la lista dos veces sin que nadie confirmara. */
	SIN_RESPUESTA,
	CASO_ASUMIDO,
	SEGUIMIENTO,
	ALERTA_RESUELTA,
	SEGUIMIENTO_CERRADO,
	/** Aviso al reloj registrado, a la espera de enviarse. */
	FCM_PENDIENTE,
	FCM_ENVIADO,
	FCM_RECIBIDO,
	/** Se agotaron los reintentos y el reloj nunca acusó el aviso. */
	FCM_NO_CONFIRMADO
}
