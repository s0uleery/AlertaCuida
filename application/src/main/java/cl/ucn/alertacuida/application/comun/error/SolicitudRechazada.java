package cl.ucn.alertacuida.application.comun.error;

/**
 * Datos que no cumplen una regla: RUN inválido, rol inexistente, contraseña débil.
 * El mensaje es para quien los escribió.
 */
public class SolicitudRechazada extends RuntimeException {

	public SolicitudRechazada(String mensaje) {
		super(mensaje);
	}

}
