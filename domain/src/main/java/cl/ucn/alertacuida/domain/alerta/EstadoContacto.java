package cl.ucn.alertacuida.domain.alerta;

/**
 * Qué pasa con el aviso a los contactos de una alerta (vista alerta_estado.estado_contacto).
 * No se guarda: se calcula con lo que informan el reloj, los contactos y el equipo.
 */
public enum EstadoContacto {
	RECIBIDA,
	AVISANDO_CONTACTO,
	CONFIRMADA,
	SIN_RESPUESTA,
	RESUELTA,
	PENDIENTE_DIA_HABIL,
	CANCELADA;

	/** @return si el reloj debe dejar de avisar a los contactos */
	public boolean detieneEscalamiento() {
		return this == CONFIRMADA || this == RESUELTA || this == CANCELADA;
	}

}
