package cl.ucn.alertacuida.domain.contacto;

import java.util.Locale;
import java.util.Objects;

/**
 * Correo electrónico normalizado: sin espacios y en minúsculas, así "Ana@Mail.cl" y "ana@mail.cl"
 * son el mismo correo y dan el mismo hash.
 */
public final class Correo {

	private static final int LARGO_MAXIMO = 254;

	private final String valor;

	/** Privado: un correo solo se crea con {@link #de(String)}, que valida y normaliza. */
	private Correo(String valor) {
		this.valor = valor;
	}

	/** @throws IllegalArgumentException si viene vacío o no parece un correo */
	public static Correo de(String texto) {
		if (texto == null || texto.isBlank()) {
			throw new IllegalArgumentException("El correo es obligatorio");
		}
		String limpio = texto.strip().toLowerCase(Locale.ROOT);
		if (limpio.length() > LARGO_MAXIMO || !limpio.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw new IllegalArgumentException("El correo no es válido");
		}
		return new Correo(limpio);
	}

	/** @return el correo normalizado */
	public String valor() {
		return valor;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Correo correo && valor.equals(correo.valor);
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
