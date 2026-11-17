package cl.ucn.alertacuida.application.aviso.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Una alerta técnica que corresponde avisar por correo a una cuenta del equipo, y que aún no está en
 * la bandeja de salida.
 *
 * @param idAlerta  la alerta
 * @param idUsuario la cuenta, activa y con recibe_avisos
 * @param momento   cuándo se recibió la alerta
 */
public record AvisoPorEncolar(UUID idAlerta, UUID idUsuario, Instant momento) {
}
