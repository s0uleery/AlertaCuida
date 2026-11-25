package cl.ucn.alertacuida.application.aviso.error;

/**
 * El canal rechazó el mensaje de forma definitiva (por ejemplo, Meta no acepta el número o la plantilla):
 * reintentarlo daría lo mismo, así que queda fallido al tiro y sale el correo de respaldo.
 */
public class EnvioRechazado extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** @param motivo qué rechazó el canal, legible para el equipo (con el código del canal si lo hay) */
	public EnvioRechazado(String motivo) {
		super(motivo);
	}

}
