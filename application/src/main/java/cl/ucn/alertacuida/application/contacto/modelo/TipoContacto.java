package cl.ucn.alertacuida.application.contacto.modelo;

/**
 * Quién es la persona respecto del paciente (tabla tipo_contacto).
 *
 * @param codigo TERCERO_SIGNIFICATIVO, CARABINEROS, SEGURIDAD_CIUDADANA, …
 * @param nombre lo que ve el equipo, por ejemplo "Tercero significativo"
 */
public record TipoContacto(String codigo, String nombre) {

	/** Familiares, vecinos y personas cercanas: único tipo que admite parentesco. */
	public static final String TERCERO_SIGNIFICATIVO = "TERCERO_SIGNIFICATIVO";

}
