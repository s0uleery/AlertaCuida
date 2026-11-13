package cl.ucn.alertacuida.application.aviso.error;

/** El canal no pudo entregar el mensaje (servidor caído, dirección rechazada, etc.). */
public class EnvioFallido extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** @param motivo qué falló, legible para el equipo */
	public EnvioFallido(String motivo) {
		super(motivo);
	}

	/**
	 * @param motivo qué falló, legible para el equipo
	 * @param causa  la excepción del canal
	 */
	public EnvioFallido(String motivo, Throwable causa) {
		super(motivo, causa);
	}

}
