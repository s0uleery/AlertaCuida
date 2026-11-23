package cl.ucn.alertacuida.application.sesion.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Un token de renovación tal como está guardado.
 *
 * @param id         identificador de la fila
 * @param idUsuario  dueño de la sesión
 * @param emitidoEn  cuándo se emitió: la última renovación, así que también mide la inactividad
 * @param expiraEn   límite de la sesión; no cambia al renovar
 * @param revocadoEn cuándo dejó de servir, o null si sigue vigente
 */
public record TokenSesion(UUID id, UUID idUsuario, Instant emitidoEn, Instant expiraEn, Instant revocadoEn) {
}
