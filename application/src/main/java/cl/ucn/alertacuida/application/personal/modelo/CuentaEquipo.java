package cl.ucn.alertacuida.application.personal.modelo;

import java.util.UUID;

/**
 * Lo que necesitan las reglas del cambio de rol sobre una cuenta ya creada.
 *
 * @param activa        si puede iniciar sesión (una pendiente o desactivada, no)
 * @param tieneTelefono si ya tiene un celular con WhatsApp registrado
 */
public record CuentaEquipo(UUID id, String codigoRol, boolean activa, boolean tieneTelefono) {
}
