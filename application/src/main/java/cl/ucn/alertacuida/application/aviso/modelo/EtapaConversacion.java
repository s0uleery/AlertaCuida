package cl.ucn.alertacuida.application.aviso.modelo;

/**
 * Etapa de una conversación de WhatsApp asociada a un intento de aviso.
 */
public enum EtapaConversacion {
	/** Plantilla inicial enviada; el contacto todavía no responde (no pausa el escalamiento del reloj). */
	ESPERANDO_ACEPTACION,
	/** El contacto pulsó "Llamaré al paciente" (pausa al reloj; límite de 10 min para responder estado). */
	ESPERANDO_ESTADO_LLAMADA,
	/** El paciente necesita atención o no contestó; se consulta disponibilidad de traslado (límite 10 min). */
	ESPERANDO_DISPONIBILIDAD,
	/** El contacto o personal se dirige al domicilio; corren los recordatorios de llegada según severidad. */
	ESPERANDO_LLEGADA,
	/** Se confirmó llegada al domicilio; se espera el resultado observado en terreno. */
	ESPERANDO_RESULTADO_VISITA,
	/** Flujo terminado con cierre parcial de la alerta (pendiente de validación PRLAC). */
	COMPLETADA,
	/** El contacto indicó que no está disponible o no puede acudir; el reloj debe escalar. */
	RECHAZADA,
	/** Se agotó el tiempo de espera de llamada o la paciencia de recordatorios de llegada; el reloj debe escalar. */
	EXPIRADA,
	/** Otra persona tomó o cerró la alerta antes de que este contacto pudiera asumirla. */
	DESCARTADA;

	/** @return si esta etapa mantiene la alerta pausada/tomada por este contacto */
	public boolean esActiva() {
		return this == ESPERANDO_ESTADO_LLAMADA || this == ESPERANDO_DISPONIBILIDAD
				|| this == ESPERANDO_LLEGADA || this == ESPERANDO_RESULTADO_VISITA;
	}

	/** @return si la conversación ya terminó y no espera más respuestas */
	public boolean esTerminal() {
		return this == COMPLETADA || this == RECHAZADA || this == EXPIRADA || this == DESCARTADA;
	}

}
