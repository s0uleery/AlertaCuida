package cl.ucn.alertacuida.application.asignacion.puerto;

import java.time.Instant;
import java.util.Optional;

/** Códigos de emparejamiento pendientes (tabla emparejamiento_reloj). Solo guarda hashes. */
public interface Emparejamientos {

	/**
	 * Guarda un código nuevo.
	 *
	 * @return false si ya hay otro pendiente con el mismo código (hay que sortear otro)
	 */
	boolean guardar(String hashCodigo, String hashToken, Instant creacion, Instant expiracion);

	/** Borra los códigos vencidos a esa hora. */
	void borrarVencidos(Instant ahora);

	/** @return cuántos códigos siguen pendientes a esa hora */
	int pendientes(Instant ahora);

	/**
	 * Usa un código: lo borra y entrega el hash del token del reloj. Llamar dentro de la transacción
	 * del vínculo, así el código vuelve a servir si el vínculo falla.
	 *
	 * @return el hash del token, o vacío si el código no existe o ya venció
	 */
	Optional<String> usar(String hashCodigo, Instant ahora);

	/** @return si hay un código pendiente, sin vencer, para ese token */
	boolean pendiente(String hashToken, Instant ahora);

}
