package cl.ucn.alertacuida.application.reloj;

import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import java.time.Clock;

/**
 * Guarda el token de Firebase que informa el reloj, para enviarle avisos por FCM. El reloj lo
 * envía al instalarse y cada vez que Firebase se lo cambia: el nuevo reemplaza al anterior.
 *
 * <p>El token es del reloj físico (smartwatch), no del vínculo: sigue sirviendo si el reloj se
 * entrega a otro paciente, hasta que Firebase lo renueve.
 */
public class RegistrarTokenFcm {

	/** Largo máximo que se acepta (docs/api-reloj.yaml). */
	static final int LARGO_MAXIMO = 4096;

	private final Relojes relojes;
	private final Clock reloj;

	/** Recibe lo que usa; lo arma ConfiguracionApiReloj. */
	public RegistrarTokenFcm(Relojes relojes, Clock reloj) {
		this.relojes = relojes;
		this.reloj = reloj;
	}

	/**
	 * @param quien el reloj que lo envía, según su token de la API
	 * @param token el token de Firebase
	 * @throws SolicitudRechazada si viene vacío o supera {@value #LARGO_MAXIMO} caracteres
	 */
	public void registrar(RelojAutenticado quien, String token) {
		String limpio = token == null ? "" : token.strip();
		if (limpio.isEmpty() || limpio.length() > LARGO_MAXIMO) {
			throw new SolicitudRechazada("El token de Firebase debe tener entre 1 y " + LARGO_MAXIMO + " caracteres");
		}
		relojes.guardarTokenFcm(quien.idDispositivo(), limpio, reloj.instant());
	}

}
