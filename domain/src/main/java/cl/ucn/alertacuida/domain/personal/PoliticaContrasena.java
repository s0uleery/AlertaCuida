package cl.ucn.alertacuida.domain.personal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Política de contraseñas del personal (1.3.2), según NIST SP 800-63B: largo mínimo y lista de
 * contraseñas comunes, sin exigir mayúsculas ni símbolos. Que no sea el RUN lo revisa quien conoce el RUN.
 */
public final class PoliticaContrasena {

	public static final int LARGO_MINIMO = 12;
	/** En bytes: bcrypt solo usa los primeros 72, así que más largo daría una falsa sensación de seguridad. */
	public static final int LARGO_MAXIMO = 72;

	private static final Set<String> COMUNES = cargarComunes();

	private PoliticaContrasena() {
	}

	/** @return el motivo del rechazo, o vacío si la contraseña sirve */
	public static Optional<String> problema(String contrasena) {
		if (contrasena == null || contrasena.length() < LARGO_MINIMO) {
			return Optional.of("La contraseña debe tener al menos " + LARGO_MINIMO + " caracteres");
		}
		if (contrasena.getBytes(StandardCharsets.UTF_8).length > LARGO_MAXIMO) {
			return Optional.of("La contraseña no puede tener más de " + LARGO_MAXIMO + " caracteres");
		}
		if (COMUNES.contains(contrasena.toLowerCase(Locale.ROOT)) || unSoloCaracter(contrasena)) {
			return Optional.of("La contraseña es demasiado común");
		}
		return Optional.empty();
	}

	private static boolean unSoloCaracter(String contrasena) {
		return contrasena.chars().distinct().count() == 1;
	}

	private static Set<String> cargarComunes() {
		InputStream lista = PoliticaContrasena.class.getResourceAsStream("contrasenas-comunes.txt");
		if (lista == null) {
			throw new IllegalStateException("Falta la lista de contraseñas comunes");
		}
		try (BufferedReader lector = new BufferedReader(new InputStreamReader(lista, StandardCharsets.UTF_8))) {
			return lector.lines()
					.map(String::strip)
					.filter(linea -> !linea.isEmpty() && !linea.startsWith("#"))
					.map(linea -> linea.toLowerCase(Locale.ROOT))
					.collect(Collectors.toUnmodifiableSet());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
