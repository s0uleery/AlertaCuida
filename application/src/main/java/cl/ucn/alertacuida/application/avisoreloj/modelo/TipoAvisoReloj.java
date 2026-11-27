package cl.ucn.alertacuida.application.avisoreloj.modelo;

/** Qué le avisa el servidor al reloj por Firebase. */
public enum TipoAvisoReloj {
	/** Un contacto respondió "Voy en camino": el reloj deja de avisar (histórico). */
	ALERTA_CONFIRMADA,
	/** Un profesional resolvió la alerta: el reloj deja de avisar (histórico). */
	ALERTA_RESUELTA,
	/** Cambió el estado de la alerta: el reloj consulta GET /alertas/{eventId} y actúa según el servidor. */
	CAMBIO_ESTADO_ALERTA,
	/** Alerta de prueba lanzada desde el dashboard. */
	SIMULACRO,
	/** Cambió la lista de contactos: el reloj debe descargar su configuración. */
	CONFIGURACION_NUEVA
}
