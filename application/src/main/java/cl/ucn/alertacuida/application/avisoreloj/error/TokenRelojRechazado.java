package cl.ucn.alertacuida.application.avisoreloj.error;

/** Firebase dice que ese token ya no sirve: el reloj tiene que registrar uno nuevo. */
public class TokenRelojRechazado extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** @param causa el error del canal */
	public TokenRelojRechazado(Throwable causa) {
		super("Firebase rechazó el token del reloj", causa);
	}

}
