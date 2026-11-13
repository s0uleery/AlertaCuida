package cl.ucn.alertacuida.application.respuesta.error;

import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;

/**
 * El enlace de respuesta no sirve: token alterado, inventado o de un aviso que no es un correo a un contacto
 * del paciente. El mensaje es el mismo para cualquier causa, para no dar pistas a quien prueba tokens.
 */
public class EnlaceRespuestaInvalido extends SolicitudRechazada {

	/** Con el mensaje para el contacto, que lo ve en la página. */
	public EnlaceRespuestaInvalido() {
		super("El enlace no es válido. Ábralo tal como le llegó en el correo.");
	}

}
