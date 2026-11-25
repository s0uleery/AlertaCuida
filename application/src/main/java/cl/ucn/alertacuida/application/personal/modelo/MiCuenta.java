package cl.ucn.alertacuida.application.personal.modelo;

/**
 * Lo que la persona ve de su propia cuenta en "Mi cuenta".
 *
 * @param nombre   como se registró
 * @param correo   adonde le llegan los avisos y los enlaces; siempre recibe el correo
 * @param telefono su celular, ya descifrado; null si no tiene
 * @param whatsapp si recibe avisos por WhatsApp (opt-in vigente)
 */
public record MiCuenta(String nombre, String correo, String telefono, boolean whatsapp) {
}
