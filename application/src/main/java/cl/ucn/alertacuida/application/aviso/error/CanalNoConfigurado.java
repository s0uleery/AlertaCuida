package cl.ucn.alertacuida.application.aviso.error;

/**
 * El canal todavía no está configurado (por ejemplo, WhatsApp antes de tener las credenciales de Meta).
 * A diferencia de {@link EnvioFallido}, reintentar no sirve: el envío queda fallido de inmediato para que
 * salga el respaldo.
 */
public class CanalNoConfigurado extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** @param motivo qué falta, legible para el equipo */
	public CanalNoConfigurado(String motivo) {
		super(motivo);
	}

}
