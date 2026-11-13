package cl.ucn.alertacuida.application.personal.modelo;

import java.time.Instant;

/**
 * Cuenta lista para guardar: inactiva y sin contraseña hasta que la persona la active.
 *
 * @param run        normalizado. El adaptador lo cifra
 * @param runHash    con el que la persona inicia sesión
 * @param correo     en minúsculas
 * @param telefono   número de WhatsApp normalizado (+569...), o vacío. El adaptador lo cifra
 * @param fechaOptIn   cuándo aceptó recibir WhatsApp, o vacío
 * @param recibeAvisos si recibe por correo los avisos de las alertas técnicas
 */
public record NuevaCuenta(String run, String runHash, String nombre, String cargo, String correo, String codigoRol,
		String telefono, Instant fechaOptIn, boolean recibeAvisos) {
}
