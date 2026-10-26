package cl.ucn.alertacuida.application.contacto.modelo;

/**
 * Quién es la persona respecto del paciente (tabla tipo_contacto).
 *
 * @param codigo FAMILIA, VECINO, PRLAC, …
 * @param nombre lo que ve el equipo, por ejemplo "Vecino o vecina"
 */
public record TipoContacto(String codigo, String nombre) {

	/** Único tipo que admite parentesco. */
	public static final String FAMILIA = "FAMILIA";

}
