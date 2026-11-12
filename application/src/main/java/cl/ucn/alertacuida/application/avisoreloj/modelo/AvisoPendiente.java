package cl.ucn.alertacuida.application.avisoreloj.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Un aviso de una alerta que el reloj aún no acusa.
 *
 * @param idDispositivo el reloj destino
 * @param token         su token de Firebase
 * @param idContacto    quién confirmó; solo en ALERTA_CONFIRMADA
 * @param intentos      cuántas veces ya se envió
 * @param envio         cuándo se envió por última vez; null si nunca
 */
public record AvisoPendiente(UUID idAviso, UUID idDispositivo, String token, TipoAvisoReloj tipo, UUID eventIdAlerta,
		UUID idContacto, int intentos, Instant envio) {

	/** @return el aviso listo para salir */
	public AvisoReloj aviso() {
		return new AvisoReloj(token, tipo, idAviso, eventIdAlerta, idContacto);
	}

}
