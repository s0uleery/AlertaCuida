package cl.ucn.alertacuida.application.asignacion.modelo;

import java.time.Instant;
import java.util.Locale;

/**
 * Lo que recibe el reloj al pedir un emparejamiento: el código corto que muestra en pantalla y el
 * token que usará cuando lo vinculen. El token solo existe en esta respuesta: la base guarda su hash.
 *
 * @param codigo            código para escribir en el dashboard, con guion (ej. "K7PM-42QX")
 * @param token             token del reloj; sirve recién cuando alguien escribe el código al vincular
 * @param expiraEn          hasta cuándo se puede usar el código
 * @param segundosConsulta  cada cuántos segundos el reloj debe preguntar si ya lo vincularon
 */
public record CodigoEmparejamiento(String codigo, String token, Instant expiraEn, int segundosConsulta) {

	/**
	 * Deja el código como se guarda su hash: sin espacios ni guiones y en mayúsculas. Así da lo mismo
	 * si quien lo escribe pone "k7pm 42qx" o "K7PM-42QX".
	 *
	 * @param codigo el que escribió la persona
	 * @return el código normalizado, o vacío si no traía nada
	 */
	public static String normalizar(String codigo) {
		return codigo == null ? "" : codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
	}

	/** Sin el token: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "CodigoEmparejamiento[codigo=" + codigo + ", expiraEn=" + expiraEn + "]";
	}

}
