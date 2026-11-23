package cl.ucn.alertacuida.application.escalamiento.modelo;

/** Por qué el servidor no envía un aviso que pidió el reloj (docs/api-reloj.yaml, RespuestaAviso). */
public enum MotivoRechazo {

	/** Pidió WhatsApp, pero la persona no aceptó recibirlo o lo revocó. */
	SIN_OPT_IN,

	/** La persona no tiene ese medio (por ejemplo, pidió correo y no tiene correo). */
	CANAL_NO_DISPONIBLE,

	/** El contacto se desactivó en la lista del paciente. */
	CONTACTO_INACTIVO,

	/** No es un contacto de la lista de este paciente. */
	CONTACTO_DESCONOCIDO,

	/** La alerta ya está confirmada, resuelta o cancelada: el reloj debe detenerse. */
	ALERTA_TERMINADA

}
