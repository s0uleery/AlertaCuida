package cl.ucn.alertacuida.domain.comun;

import java.util.Objects;

/**
 * RUN chileno normalizado: sin puntos, con guion y la K en mayúscula (ej. 12345678-5).
 * Así 12.345.678-5 y 12345678-5 son el mismo RUN y dan el mismo hash al iniciar sesión.
 */
public final class Run {

	private final String valor;

	/** Privado: un Run solo se crea con {@link #de(String)}, que valida y normaliza. */
	private Run(String valor) {
		this.valor = valor;
	}

	/** Normaliza y valida el dígito verificador (módulo 11). */
	public static Run de(String texto) {
		if (texto == null) {
			throw new IllegalArgumentException("El RUN es obligatorio");
		}
		String limpio = texto.replace(".", "").replace("-", "").replace(" ", "").trim().toUpperCase();
		if (!limpio.matches("\\d{7,8}[0-9K]")) {
			throw new IllegalArgumentException("El RUN no tiene un formato válido");
		}
		String cuerpo = limpio.substring(0, limpio.length() - 1);
		char verificador = limpio.charAt(limpio.length() - 1);
		if (verificador != digitoVerificador(cuerpo)) {
			throw new IllegalArgumentException("El dígito verificador del RUN no corresponde");
		}
		return new Run(cuerpo + "-" + verificador);
	}

	/**
	 * Dígito verificador módulo 11: multiplica los dígitos de derecha a izquierda por 2, 3, … 7 (y vuelve a 2).
	 *
	 * @param cuerpo el RUN sin el dígito verificador, solo números
	 * @return '0' a '9' o 'K'
	 */
	static char digitoVerificador(String cuerpo) {
		int suma = 0;
		int factor = 2;
		for (int i = cuerpo.length() - 1; i >= 0; i--) {
			suma += (cuerpo.charAt(i) - '0') * factor;
			factor = factor == 7 ? 2 : factor + 1;
		}
		int resto = 11 - suma % 11;
		if (resto == 11) {
			return '0';
		}
		return resto == 10 ? 'K' : (char) ('0' + resto);
	}

	/** @return el RUN normalizado, por ejemplo "12345678-5" */
	public String valor() {
		return valor;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Run run && valor.equals(run.valor);
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
