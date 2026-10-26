package cl.ucn.alertacuida.application.comun.error;

/**
 * Datos que no cumplen una regla: RUN inválido, rol inexistente, contraseña débil.
 * El mensaje es para quien los escribió.
 */
public class SolicitudRechazada extends RuntimeException {

	/** @param mensaje por ejemplo, "La contraseña debe tener al menos 12 caracteres" */
	public SolicitudRechazada(String mensaje) {
		super(mensaje);
	}

}
