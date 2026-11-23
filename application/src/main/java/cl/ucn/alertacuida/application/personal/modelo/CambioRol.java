package cl.ucn.alertacuida.application.personal.modelo;

/**
 * Lo que pide quien cambia el rol de una cuenta.
 *
 * @param codigoRol      rol nuevo
 * @param telefono       celular con WhatsApp; solo se pide al pasar a COORDINADOR a una cuenta que no
 *                       lo tiene. Vacío deja el teléfono como está
 * @param aceptaWhatsapp si la persona aceptó recibir los avisos por WhatsApp (opt-in de Meta)
 */
public record CambioRol(String codigoRol, String telefono, boolean aceptaWhatsapp) {
}
