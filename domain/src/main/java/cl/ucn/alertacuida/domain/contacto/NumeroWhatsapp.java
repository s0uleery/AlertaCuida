package cl.ucn.alertacuida.domain.contacto;

import java.util.Objects;
import java.util.Set;

/**
 * Número de WhatsApp normalizado en formato internacional (ej. +56912345678). Así "9 1234 5678"
 * y "+56 9 1234 5678" son el mismo número y dan el mismo hash.
 *
 * <p>Los números de emergencia (SAMU 131, Bomberos 132, Carabineros 133) no pueden ser contactos:
 * no reciben WhatsApp, y el mensaje de una alerta grave ya pide llamar al 131.
 */
public final class NumeroWhatsapp {

	private static final Set<String> EMERGENCIAS = Set.of("130", "131", "132", "133", "134");

	private final String valor;

	/** Privado: un número solo se crea con {@link #de(String)}, que valida y normaliza. */
	private NumeroWhatsapp(String valor) {
		this.valor = valor;
	}

	/**
	 * Acepta un celular chileno de 9 dígitos (9 1234 5678), con 56 delante, o cualquier número
	 * internacional que empiece con +.
	 *
	 * @throws IllegalArgumentException si viene vacío, es un número de emergencia o no tiene un formato válido
	 */
	public static NumeroWhatsapp de(String texto) {
		if (texto == null || texto.isBlank()) {
			throw new IllegalArgumentException("El número de WhatsApp es obligatorio");
		}
		String limpio = texto.replaceAll("[\\s().-]", "");
		String digitos = limpio.startsWith("+") ? limpio.substring(1) : limpio;
		if (EMERGENCIAS.contains(digitos)) {
			throw new IllegalArgumentException("Los números de emergencia, como el del SAMU (131), no pueden ser "
					+ "contactos automáticos");
		}
		if (limpio.startsWith("+") && digitos.matches("[1-9]\\d{7,14}")) {
			return new NumeroWhatsapp("+" + digitos);
		}
		if (digitos.matches("9\\d{8}")) {
			return new NumeroWhatsapp("+56" + digitos);
		}
		if (digitos.matches("569\\d{8}")) {
			return new NumeroWhatsapp("+" + digitos);
		}
		throw new IllegalArgumentException("El número de WhatsApp no es válido: usa un celular como +56 9 1234 5678");
	}

	/** @return el número normalizado, por ejemplo "+56912345678" */
	public String valor() {
		return valor;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof NumeroWhatsapp numero && valor.equals(numero.valor);
	}

	@Override
	public int hashCode() {
		return Objects.hash(valor);
	}

	@Override
	public String toString() {
		return valor;
	}

}
