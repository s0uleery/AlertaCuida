package cl.ucn.alertacuida.application.sesion.error;

/**
 * El token de renovación no sirve, por cualquier causa: no existe, venció, hubo inactividad, ya se
 * usó o la cuenta se desactivó. El mensaje es siempre el mismo, para no dar pistas a quien lo pruebe.
 */
public class SesionNoRenovable extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** Crea el error con el mensaje uniforme. */
	public SesionNoRenovable() {
		super("La sesión terminó. Inicia sesión de nuevo.");
	}

}
