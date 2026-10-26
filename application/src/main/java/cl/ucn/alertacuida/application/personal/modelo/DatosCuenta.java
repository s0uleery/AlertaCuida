package cl.ucn.alertacuida.application.personal.modelo;

/**
 * Lo que escribe el administrador al crear la cuenta de una persona del equipo.
 *
 * @param run       con o sin puntos
 * @param cargo     opcional
 * @param codigoRol ADMINISTRADOR, COORDINADOR, PERSONAL u otro rol activo
 */
public record DatosCuenta(String run, String nombre, String cargo, String correo, String codigoRol) {
}
