package cl.ucn.alertacuida.application.personal.modelo;

/**
 * Cuenta lista para guardar: inactiva y sin contraseña hasta que la persona la active.
 *
 * @param run     normalizado. Va en la columna rut, que se cifrará más adelante
 * @param runHash con el que la persona inicia sesión
 * @param correo  en minúsculas
 */
public record NuevaCuenta(String run, String runHash, String nombre, String cargo, String correo, String codigoRol) {
}
