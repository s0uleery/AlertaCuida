package cl.ucn.alertacuida.application.personal.error;

/** La cuenta no existe o ya está activada: no tiene sentido un enlace de activación. */
public class CuentaNoPendiente extends RuntimeException {

	public CuentaNoPendiente() {
		super("No hay una cuenta pendiente de activar con ese identificador");
	}

}
