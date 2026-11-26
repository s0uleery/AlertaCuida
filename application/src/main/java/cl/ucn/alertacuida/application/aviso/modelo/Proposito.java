package cl.ucn.alertacuida.application.aviso.modelo;

/**
 * Para qué es un aviso (registro_intento.proposito). Decide qué texto lleva y permite que una misma cuenta
 * reciba, de la misma alerta y por el mismo canal, un aviso de cada propósito.
 */
public enum Proposito {

	/** El que pidió el reloj, el de la oficina, el de recibe_avisos o el respaldo de un WhatsApp. */
	AVISO,

	/** Al equipo: la alerta no avanza porque el reloj dejó de pedir avisos (vigilante del escalamiento). */
	VIGILANTE,

	/** A los coordinadores: la alerta pasó a grave después de su primer aviso. */
	AGRAVAMIENTO,

	/** A quien tiene la alerta a cargo: el personal asignado aún no llega (pasos de la oficina, N1-08). */
	RECORDATORIO

}
