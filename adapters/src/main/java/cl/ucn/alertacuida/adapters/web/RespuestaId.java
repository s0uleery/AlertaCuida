package cl.ucn.alertacuida.adapters.web;

import java.util.UUID;

/**
 * Respuesta de un alta (201): el identificador de lo creado, para abrirlo después.
 *
 * @param id identificador interno (UUID)
 */
public record RespuestaId(UUID id) {
}
