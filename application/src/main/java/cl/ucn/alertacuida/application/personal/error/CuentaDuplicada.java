package cl.ucn.alertacuida.application.personal.error;

/** Ya hay una cuenta con ese RUN o ese correo. */
public class CuentaDuplicada extends RuntimeException {

	public CuentaDuplicada(String mensaje) {
		super(mensaje);
	}

}
