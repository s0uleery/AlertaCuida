package cl.ucn.alertacuida.application.respuesta.modelo;

/**
 * Cada botón de los pasos de la oficina PRLAC en la página del enlace del correo. Son los mismos de los
 * mensajes de WhatsApp N1-02 a N1-11 (decisiones del 26-11-2026). Cada uno sirve solo en su etapa
 * ({@link EtapaOficina}).
 */
public enum PasoOficina {

	/** "Tomo la alerta": queda a cargo. El reloj sigue avisando. */
	TOMAR,

	/** Resultado de la llamada (N1-02): la persona está bien. Confirma la alerta. */
	PACIENTE_BIEN,

	/** Resultado de la llamada (N1-02): necesita atención. */
	NECESITA_ATENCION,

	/** Resultado de la llamada (N1-02): no contesta. */
	NO_CONTESTA,

	/** Cierre (N1-03): el evento sí ocurrió. Cierra como POSITIVA. */
	OCURRIO,

	/** Cierre (N1-03): fue una falsa alarma. */
	FALSA_ALARMA,

	/** Visita (N1-05): hay personal asignado para ir. Confirma la alerta. */
	PERSONAL_ASIGNADO,

	/** Visita (N1-05): no hay personal disponible (N1-06). El reloj sigue avisando a los contactos. */
	SIN_DISPONIBILIDAD,

	/** Llegada (N1-07, N1-08): el personal ya llegó al domicilio. */
	LLEGO,

	/** Recordatorio (N1-08): el personal sigue en camino. Queda como nota en el seguimiento. */
	SIGUE_EN_CAMINO,

	/** Resultado de la visita (N1-09): la persona está bien. */
	VISITA_BIEN,

	/** Resultado de la visita (N1-09, N1-10): apoyo no urgente. Cierra como POSITIVA. */
	APOYO_NO_URGENTE,

	/** Resultado de la visita (N1-09, N1-11): emergencia. */
	EMERGENCIA,

	/** Desenlace de la emergencia (N1-11): trasladada. Cierra como POSITIVA y deriva a SAMU. */
	TRASLADADA,

	/** Desenlace de la emergencia (N1-11): atendida en casa. Cierra como POSITIVA. */
	ATENDIDA_EN_CASA,

	/** Desenlace de la emergencia (N1-11): otro resultado. Cierra como POSITIVA. */
	OTRO_RESULTADO,

	/** Comentario del responsable después del cierre (N1-04): se agrega a las observaciones. */
	COMENTARIO

}
