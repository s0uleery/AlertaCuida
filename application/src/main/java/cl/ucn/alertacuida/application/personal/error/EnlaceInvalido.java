package cl.ucn.alertacuida.application.personal.error;

/**
 * Enlace de activación inexistente, vencido, ya usado o reemplazado por otro.
 * Un solo mensaje para cualquier causa: no revela qué enlaces existieron.
 */
public class EnlaceInvalido extends RuntimeException {

	public EnlaceInvalido() {
		super("El enlace no es válido o ya venció. Pide uno nuevo al administrador");
	}

}
