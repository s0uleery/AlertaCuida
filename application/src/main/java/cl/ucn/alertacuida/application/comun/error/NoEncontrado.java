package cl.ucn.alertacuida.application.comun.error;

/** Lo pedido no existe (responde 404). El mensaje es para quien hizo la petición. */
public class NoEncontrado extends RuntimeException {

	/** @param mensaje por ejemplo, "No existe ese paciente" */
	public NoEncontrado(String mensaje) {
		super(mensaje);
	}

}
