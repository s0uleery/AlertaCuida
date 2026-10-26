package cl.ucn.alertacuida.application.personal.error;

/** La cuenta no existe o ya está activada: no tiene sentido un enlace de activación. */
public class CuentaNoPendiente extends RuntimeException {

	/** Crea el error con su mensaje fijo. */
	public CuentaNoPendiente() {
		super("No hay una cuenta pendiente de activar con ese identificador");
	}

}
