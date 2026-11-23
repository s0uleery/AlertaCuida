package cl.ucn.alertacuida.application.aviso.puerto;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que pasó con cada envío de la bandeja de salida, sea correo o WhatsApp (columnas de envío,
 * reintento y fallo de registro_intento).
 */
public interface RegistroEnvios {

	/** Lo registra como enviado y deja de estar pendiente. */
	void marcarEnviado(UUID idIntento, String idExterno, Instant ahora);

	/**
	 * Deja el envío pendiente para más tarde.
	 *
	 * @param reintentos cuántos reintentos se habrán hecho al enviarlo de nuevo
	 * @param proximo    cuándo se reintenta
	 */
	void reprogramar(UUID idIntento, int reintentos, Instant proximo);

	/** Lo registra como fallido, con el motivo, y deja de estar pendiente. */
	void marcarFallido(UUID idIntento, String motivo, Instant ahora);

}
