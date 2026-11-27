package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.Locale;
import java.util.Optional;

/**
 * Acciones de botón reconocidas por el flujo conversacional de WhatsApp (plantillas aprobadas de Meta y botones
 * interactivos de seguimiento). Acepta tanto el payload interno como el texto visible del botón de plantilla por
 * si Meta devuelve el texto cuando no se sobrescribe el payload.
 */
public enum AccionWhatsapp {
	// Botones de plantillas iniciales
	TOMAR_LLAMADA,
	ATENDEREMOS,
	ATENDER_SIMULACRO,
	NO_DISPONIBLE,
	// Compatibilidad con payloads anteriores
	VOY_EN_CAMINO,
	NO_PUEDO,
	// Paso "¿Cómo está la persona?" (Nivel 1 y Nivel 2)
	LLAMADA_BIEN,
	LLAMADA_NECESITA_ATENCION,
	LLAMADA_NO_CONTESTA,
	// Paso de disponibilidad (Nivel 1 y Nivel 2)
	PERSONAL_ASIGNADO,
	SIN_DISPONIBILIDAD,
	ATENDERE,
	NO_PUEDO_ACUDIR,
	// Paso de confirmación de llegada (todos los niveles)
	LLEGO,
	AUN_NO_LLEGA,
	// Paso de resultado en terreno (todos los niveles)
	RESULTADO_BIEN,
	RESULTADO_APOYO_NO_URGENTE,
	RESULTADO_EMERGENCIA;

	/**
	 * @param valor payload o texto recibido desde el webhook de Meta
	 * @return la acción correspondiente, o vacío si el botón no es de AlertaCuida
	 */
	public static Optional<AccionWhatsapp> desde(String valor) {
		if (valor == null || valor.isBlank()) {
			return Optional.empty();
		}
		String limpio = valor.strip();
		try {
			return Optional.of(AccionWhatsapp.valueOf(limpio));
		} catch (IllegalArgumentException noEsNombreEnum) {
			return porTextoVisible(limpio.toLowerCase(Locale.ROOT));
		}
	}

	/** Reconoce el texto exacto de los botones de las plantillas aprobadas en Meta. */
	private static Optional<AccionWhatsapp> porTextoVisible(String texto) {
		return switch (texto) {
			case "llamaré al paciente", "llamare al paciente" -> Optional.of(TOMAR_LLAMADA);
			case "atenderemos" -> Optional.of(ATENDEREMOS);
			case "atender simulacro" -> Optional.of(ATENDER_SIMULACRO);
			case "no disponible" -> Optional.of(NO_DISPONIBLE);
			case "voy en camino" -> Optional.of(VOY_EN_CAMINO);
			case "no puedo", "no puedo asistir" -> Optional.of(NO_PUEDO);
			default -> Optional.empty();
		};
	}

}
