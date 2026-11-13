package cl.ucn.alertacuida.application.respuesta.modelo;

/**
 * Lo que responde un contacto del paciente a un aviso: con el botón del WhatsApp o con el enlace de su
 * correo. Los nombres son los de la columna registro_intento.respuesta.
 */
public enum RespuestaContacto {

	/** Va a ver a la persona: confirma la alerta y el reloj deja de avisar a los demás. */
	VOY_EN_CAMINO,

	/** No puede ir: queda registrado y el reloj sigue con el siguiente contacto. */
	NO_PUEDO

}
