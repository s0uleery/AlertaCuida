package cl.ucn.alertacuida.application.asignacion.error;

/**
 * Hay demasiados códigos de emparejamiento pendientes (responde 429). Protege la ruta pública que
 * los entrega: el reloj debe esperar y volver a pedir uno.
 */
public class DemasiadosEmparejamientos extends RuntimeException {

	/** @param mensaje explicación para el registro y la respuesta */
	public DemasiadosEmparejamientos(String mensaje) {
		super(mensaje);
	}

}
