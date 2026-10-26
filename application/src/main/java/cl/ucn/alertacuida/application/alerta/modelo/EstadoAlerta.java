package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.util.UUID;

/**
 * Lo que el reloj necesita saber de su alerta para seguir avisando o detenerse.
 *
 * @param severidadActual     GRAVE si se agravó; si no, la que tenía al llegar
 * @param detenerEscalamiento si está confirmada, resuelta o cancelada
 * @param confirmadaPor       null mientras nadie haya respondido "Voy en camino"
 */
public record EstadoAlerta(UUID eventId, EstadoContacto estado, Severidad severidadActual,
		boolean detenerEscalamiento, ConfirmacionContacto confirmadaPor) {

	/** @return el estado, calculando si el reloj debe detenerse */
	public static EstadoAlerta de(UUID eventId, EstadoContacto estado, Severidad severidadActual,
			ConfirmacionContacto confirmadaPor) {
		return new EstadoAlerta(eventId, estado, severidadActual, estado.detieneEscalamiento(), confirmadaPor);
	}

}
