package cl.ucn.alertacuida.application.escalamiento.modelo;

/** Qué pasó con una solicitud de aviso del reloj. */
public enum ResultadoAviso {

	/** El servidor lo envía (si WhatsApp falla, intenta por correo). */
	ACEPTADO,

	/** No se envía; el reloj pasa al siguiente contacto. Es parte normal del flujo, no un error. */
	RECHAZADO

}
