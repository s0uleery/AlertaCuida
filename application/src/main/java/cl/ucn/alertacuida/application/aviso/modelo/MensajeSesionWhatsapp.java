package cl.ucn.alertacuida.application.aviso.modelo;

import java.util.List;
import java.util.Objects;

/**
 * Un mensaje de sesión enviado dentro de la ventana de atención al cliente de 24 horas de Meta: puede ser texto
 * simple (sin botones) o un mensaje interactivo con entre 1 y 3 botones de respuesta rápida.
 *
 * @param telefono número destino en formato +569…
 * @param cuerpo   texto principal del mensaje
 * @param botones  entre 0 y 3 botones; vacío = mensaje de texto simple
 */
public record MensajeSesionWhatsapp(String telefono, String cuerpo, List<BotonInteractivo> botones) {

	/** Máximo de botones interactivos que acepta Meta por mensaje. */
	public static final int MAXIMO_BOTONES = 3;

	/** Valida destinatario, cuerpo y tope de botones. */
	public MensajeSesionWhatsapp {
		Objects.requireNonNull(telefono, "telefono");
		Objects.requireNonNull(cuerpo, "cuerpo");
		if (telefono.isBlank()) {
			throw new IllegalArgumentException("Falta el teléfono destino");
		}
		if (cuerpo.isBlank()) {
			throw new IllegalArgumentException("Falta el cuerpo del mensaje");
		}
		botones = botones == null ? List.of() : List.copyOf(botones);
		if (botones.size() > MAXIMO_BOTONES) {
			throw new IllegalArgumentException("WhatsApp admite como máximo " + MAXIMO_BOTONES + " botones");
		}
	}

	/** @return un mensaje de texto simple sin botones */
	public static MensajeSesionWhatsapp texto(String telefono, String cuerpo) {
		return new MensajeSesionWhatsapp(telefono, cuerpo, List.of());
	}

}
