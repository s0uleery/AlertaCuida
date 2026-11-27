package cl.ucn.alertacuida.application.aviso.modelo;

/**
 * Nivel del flujo conversacional de WhatsApp Business (Flujo de mensajes Alertas v3).
 * Cada nivel define tanto la plantilla inicial como las preguntas de seguimiento.
 */
public enum NivelConversacion {
	/** Encargado / equipo PRLAC: llamada previa, coordinación de personal en terreno y llegada. */
	NIVEL_1_PRLAC,
	/** Tercero significativo / red cercana: llamada previa, consulta de asistencia y llegada. */
	NIVEL_2_TERCERO,
	/** Servicios públicos (Seguridad Ciudadana, Carabineros, instituciones): despacho directo y llegada. */
	NIVEL_3_SERVICIO,
	/** Simulacro: recorre el flujo completo de atención para probar toda la cadena. */
	SIMULACRO;

	/**
	 * Determina el nivel de conversación según el tipo de aviso y el código de tipo de contacto.
	 *
	 * @param esSimulacro  si la alerta es un simulacro
	 * @param alEquipo     si el aviso va a un contacto interno del equipo PRLAC
	 * @param tipoContacto código de tipo_contacto del vínculo, o null si es interno
	 * @return el nivel correspondiente
	 */
	public static NivelConversacion de(boolean esSimulacro, boolean alEquipo, String tipoContacto) {
		if (esSimulacro) {
			return SIMULACRO;
		}
		if (alEquipo || "PRLAC".equals(tipoContacto)) {
			return NIVEL_1_PRLAC;
		}
		if ("CARABINEROS".equals(tipoContacto) || "SEGURIDAD_CIUDADANA".equals(tipoContacto)
				|| "INSTITUCION".equals(tipoContacto) || "CESFAM".equals(tipoContacto)) {
			return NIVEL_3_SERVICIO;
		}
		return NIVEL_2_TERCERO;
	}

}
