package cl.ucn.alertacuida.application.sesion;

import cl.ucn.alertacuida.application.sesion.puerto.TokensSesion;
import java.time.Clock;

/** Borra los tokens de renovación vencidos de todas las cuentas, incluidas las que ya no inician sesión. */
public class LimpiarTokensVencidos {

	private final TokensSesion tokens;
	private final Clock reloj;

	/**
	 * @param tokens dónde están los tokens de renovación
	 * @param reloj  hora actual
	 */
	public LimpiarTokensVencidos(TokensSesion tokens, Clock reloj) {
		this.tokens = tokens;
		this.reloj = reloj;
	}

	/** Borra los que ya alcanzaron el límite de su sesión. */
	public void limpiar() {
		tokens.eliminarVencidos(reloj.instant());
	}

}
