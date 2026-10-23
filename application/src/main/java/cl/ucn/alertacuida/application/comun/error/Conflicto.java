package cl.ucn.alertacuida.application.comun.error;

/**
 * La petición choca con lo que ya está guardado (responde 409): un RUN repetido, un reloj que ya
 * está vinculado a otro paciente… El mensaje dice qué hay que resolver primero.
 */
public class Conflicto extends RuntimeException {

	/** @param mensaje por ejemplo, "Ya existe un paciente con ese RUN" */
	public Conflicto(String mensaje) {
		super(mensaje);
	}

}
