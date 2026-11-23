package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.contacto.Canal;
import java.time.Instant;

/**
 * Un paso de la línea de tiempo de una alerta. Los campos que no corresponden a ese paso van en null.
 *
 * @param contacto a quién se avisó: una persona de la lista o una cuenta del equipo
 * @param canal    por dónde se avisó
 * @param ronda    vuelta de la lista (1 o 2); null en los avisos al equipo
 * @param detalle  motivo del fallo, respuesta del contacto, tipo de aviso al reloj o nota de seguimiento
 * @param usuario  profesional que hizo el paso
 */
public record PasoAlerta(Instant fechaHora, TipoPaso tipo, String contacto, Canal canal, Integer ronda,
		String detalle, String usuario) {

	/** @return un paso sin contacto ni profesional */
	public static PasoAlerta simple(Instant fechaHora, TipoPaso tipo, String detalle) {
		return new PasoAlerta(fechaHora, tipo, null, null, null, detalle, null);
	}

}
