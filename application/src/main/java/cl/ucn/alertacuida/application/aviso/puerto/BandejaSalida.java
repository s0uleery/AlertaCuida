package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Salida de la bandeja de correos: los que toca enviar y lo que pasó con cada uno (columnas de envío,
 * reintento y fallo de registro_intento).
 */
public interface BandejaSalida {

	/** @return los correos cuyo próximo envío ya llegó, el más antiguo primero */
	List<CorreoPendiente> pendientes(Instant ahora);

	/** Lo registra como enviado y deja de estar pendiente. */
	void marcarEnviado(UUID idIntento, String idExterno, Instant ahora);

	/**
	 * Deja el correo pendiente para más tarde.
	 *
	 * @param reintentos cuántos reintentos se habrán hecho al enviarlo de nuevo
	 * @param proximo    cuándo se reintenta
	 */
	void reprogramar(UUID idIntento, int reintentos, Instant proximo);

	/** Lo registra como fallido, con el motivo, y deja de estar pendiente. */
	void marcarFallido(UUID idIntento, String motivo, Instant ahora);

}
