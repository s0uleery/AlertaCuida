package cl.ucn.alertacuida.application.avisoreloj.modelo;

/** Qué le avisa el servidor al reloj por Firebase. */
public enum TipoAvisoReloj {
	/** Un contacto respondió "Voy en camino": el reloj deja de avisar. */
	ALERTA_CONFIRMADA,
	/** Un profesional resolvió la alerta: el reloj deja de avisar. */
	ALERTA_RESUELTA,
	/** Alerta de prueba lanzada desde el dashboard. */
	SIMULACRO,
	/** Cambió la lista de contactos: el reloj debe descargar su configuración. */
	CONFIGURACION_NUEVA
}
