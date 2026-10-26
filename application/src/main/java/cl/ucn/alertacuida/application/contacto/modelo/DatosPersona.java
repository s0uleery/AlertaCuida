package cl.ucn.alertacuida.application.contacto.modelo;

/**
 * Lo que se guarda de una persona en la tabla contacto, ya normalizado. El adaptador cifra
 * nombre, WhatsApp y correo; los hash permiten buscarla sin descifrar.
 *
 * @param nombre       nombre de la persona o institución
 * @param whatsapp     número normalizado, o vacío
 * @param whatsappHash hash del número, o vacío
 * @param correo       correo normalizado, o vacío
 * @param correoHash   hash del correo, o vacío
 */
public record DatosPersona(String nombre, String whatsapp, String whatsappHash, String correo, String correoHash) {
}
