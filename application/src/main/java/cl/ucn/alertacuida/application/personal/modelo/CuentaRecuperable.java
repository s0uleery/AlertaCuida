package cl.ucn.alertacuida.application.personal.modelo;

import java.util.UUID;

/**
 * Cuenta del equipo que puede recuperar su contraseña: activa y con contraseña.
 *
 * @param id      la cuenta
 * @param runHash hash del RUN, para revisar que la contraseña nueva no sea el propio RUN
 * @param nombre  nombre de la persona, para saludarla en el correo
 * @param correo  adonde va el enlace; puede faltar, y entonces no se envía nada
 */
public record CuentaRecuperable(UUID id, String runHash, String nombre, String correo) {
}
