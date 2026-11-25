package cl.ucn.alertacuida.application.comun.error;

/**
 * La petición choca con lo que ya está guardado (responde 409): un RUN repetido, un reloj que ya
 * está vinculado a otro paciente… El mensaje dice qué hay que resolver primero.
 */
public class Conflicto extends RuntimeException {

	private final String codigo;

	/** @param mensaje por ejemplo, "Ya existe un paciente con ese RUN" */
	public Conflicto(String mensaje) {
		this(mensaje, null);
	}

	/**
	 * @param mensaje el texto para mostrar
	 * @param codigo  para que el dashboard distinga este conflicto de otros y ofrezca una salida (ej.
	 *                "CON_HISTORIAL"); viaja como {@code codigo} en la respuesta
	 */
	public Conflicto(String mensaje, String codigo) {
		super(mensaje);
		this.codigo = codigo;
	}

	/** @return el código, o null si el mensaje basta */
	public String codigo() {
		return codigo;
	}

}
