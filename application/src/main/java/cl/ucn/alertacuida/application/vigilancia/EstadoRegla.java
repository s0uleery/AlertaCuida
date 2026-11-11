package cl.ucn.alertacuida.application.vigilancia;

/** Resultado de evaluar una regla de ausencia sobre un reloj. */
public enum EstadoRegla {

	/** El problema existe: corresponde una alerta, si no hay una abierta. */
	ACTIVA,

	/** El reloj volvió a la normalidad: si había una alerta abierta, se cierra. */
	NORMAL,

	/** No alcanza para decidir: no se crea ni se cierra nada. */
	SIN_CAMBIO

}
