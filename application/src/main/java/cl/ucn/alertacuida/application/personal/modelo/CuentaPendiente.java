package cl.ucn.alertacuida.application.personal.modelo;

import java.util.UUID;

/**
 * Cuenta creada que aún no tiene contraseña.
 *
 * @param id      la cuenta
 * @param runHash hash del RUN, para revisar que quien activa sea la misma persona
 * @param nombre  nombre de la persona, para saludarla en el correo de activación
 * @param correo  adonde va el correo de activación
 */
public record CuentaPendiente(UUID id, String runHash, String nombre, String correo) {
}
