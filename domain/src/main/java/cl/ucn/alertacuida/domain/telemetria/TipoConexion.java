package cl.ucn.alertacuida.domain.telemetria;

/** Por dónde envió el reloj su sincronización (sincronizacion.tipo_conexion). */
public enum TipoConexion {
	/** Por su propia SIM. */
	LTE,
	/** Por una red Wi-Fi. */
	WIFI,
	/** A través del teléfono emparejado por Bluetooth. */
	BLUETOOTH
}
