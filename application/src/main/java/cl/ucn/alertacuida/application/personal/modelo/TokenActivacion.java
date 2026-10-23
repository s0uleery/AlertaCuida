package cl.ucn.alertacuida.application.personal.modelo;

import java.time.Instant;
import java.util.UUID;

/** Un enlace de activación sin usar, tal como está guardado. */
public record TokenActivacion(UUID id, UUID idUsuario, Instant expiraEn) {
}
