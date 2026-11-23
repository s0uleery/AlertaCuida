package cl.ucn.alertacuida.application.asignacion.modelo;

/** Lo que responde el servidor cuando el reloj pregunta si ya lo vincularon. */
public enum EstadoEmparejamiento {

	/** Nadie ha escrito el código todavía: seguir preguntando. */
	PENDIENTE,

	/** Ya lo vincularon: su token sirve para la API del reloj. */
	VINCULADO,

	/** El código venció o el token no corresponde a nada: pedir un código nuevo. */
	VENCIDO

}
