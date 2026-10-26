package cl.ucn.alertacuida.application.asignacion.modelo;

import java.util.UUID;

/**
 * Llave con que el reloj se identifica ante el servidor (rol DEVICE). Solo existe en esta
 * respuesta: la base guarda su hash. Quien instala el reloj la ingresa en su app; si se pierde,
 * se genera otra y la anterior deja de servir.
 *
 * @param idAsignacion vínculo al que pertenece; al cerrarlo, el token deja de servir
 */
public record TokenReloj(UUID idAsignacion, String token) {

	/** Sin el token: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "TokenReloj[idAsignacion=" + idAsignacion + "]";
	}

}
