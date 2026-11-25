package cl.ucn.alertacuida.application.reloj.puerto;

import java.util.UUID;

/**
 * Borrado de relojes del inventario. Solo se borra un reloj que nunca tuvo paciente: las lecturas y alertas
 * cuelgan de sus vínculos, y borrarlo las perdería.
 */
public interface BajaRelojes {

	/**
	 * @param id reloj revisado
	 * @return si tuvo o tiene algún vínculo con un paciente
	 */
	boolean tuvoAsignaciones(UUID id);

	/**
	 * Borra el reloj solo si nunca tuvo un vínculo, en la misma sentencia: si alguien lo vincula a la vez, no
	 * se borra.
	 *
	 * @param id reloj a borrar
	 * @return si lo borró
	 */
	boolean eliminarSinHistorial(UUID id);

}
