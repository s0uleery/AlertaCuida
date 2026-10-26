package cl.ucn.alertacuida.application.telemetria.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Un lote ya guardado, para responder igual si el reloj lo reenvía.
 *
 * @param lecturas cuántas lecturas se guardaron con este lote
 */
public record SincronizacionGuardada(UUID id, UUID idAsignacion, Instant recepcion, int lecturas) {
}
