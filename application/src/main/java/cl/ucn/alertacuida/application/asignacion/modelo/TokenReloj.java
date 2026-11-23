package cl.ucn.alertacuida.application.asignacion.modelo;

import java.util.UUID;

/**
 * Resultado de vincular un reloj. Si se vinculó con código de emparejamiento, el reloj ya tiene su
 * token y {@code token} viene vacío. Si fue sin código (desarrollo), trae la llave con que el reloj se
 * identifica (rol DEVICE): solo existe en esta respuesta, la base guarda su hash, y quien instala el
 * reloj la ingresa a mano; si se pierde, se genera otra y la anterior deja de servir.
 *
 * @param idAsignacion vínculo al que pertenece; al cerrarlo, el token deja de servir
 * @param token        el token nuevo, o null si el reloj se emparejó con código
 */
public record TokenReloj(UUID idAsignacion, String token) {

	/** Sin el token: que no aparezca en los logs. */
	@Override
	public String toString() {
		return "TokenReloj[idAsignacion=" + idAsignacion + "]";
	}

}
