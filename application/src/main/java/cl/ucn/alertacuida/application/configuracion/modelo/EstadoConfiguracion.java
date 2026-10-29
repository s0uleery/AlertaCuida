package cl.ucn.alertacuida.application.configuracion.modelo;

/** Si el reloj de un paciente ya aplica la configuración vigente, según su última sincronización. */
public enum EstadoConfiguracion {

	/** La última sincronización informa la versión vigente. */
	AL_DIA,
	/** La última sincronización informa otra versión (o ninguna): el reloj aún no la descarga. */
	PENDIENTE,
	/** El reloj vigente todavía no se ha sincronizado. */
	SIN_SINCRONIZAR,
	/** El paciente no tiene reloj. */
	SIN_RELOJ

}
