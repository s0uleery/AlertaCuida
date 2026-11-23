package cl.ucn.alertacuida.application.personal.modelo;

/**
 * Lo que escribe el administrador al crear la cuenta de una persona del equipo.
 *
 * @param run             con o sin puntos
 * @param cargo           opcional
 * @param correo          recibe los enlaces y el respaldo de los avisos
 * @param codigoRol       ADMINISTRADOR, COORDINADOR, PERSONAL u otro rol activo
 * @param telefono        celular con WhatsApp; obligatorio para el rol COORDINADOR, que recibe los
 *                        avisos de la oficina PRLAC
 * @param aceptaWhatsapp  si la persona aceptó recibir esos avisos por WhatsApp (opt-in de Meta)
 * @param recibeAvisos    si recibe por correo los avisos de las alertas técnicas; apagado por defecto
 */
public record DatosCuenta(String run, String nombre, String cargo, String correo, String codigoRol, String telefono,
		boolean aceptaWhatsapp, boolean recibeAvisos) {
}
