package cl.ucn.alertacuida.application.personal.error;

/** Ya hay una cuenta con ese RUN o ese correo. */
public class CuentaDuplicada extends RuntimeException {

	/** @param mensaje dice si se repite el RUN o el correo */
	public CuentaDuplicada(String mensaje) {
		super(mensaje);
	}

}
