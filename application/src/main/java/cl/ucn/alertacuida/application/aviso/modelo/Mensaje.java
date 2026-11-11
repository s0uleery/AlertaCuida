package cl.ucn.alertacuida.application.aviso.modelo;

/**
 * Un mensaje listo para enviar por un canal (hoy, el correo institucional).
 *
 * @param destinatario dirección de destino
 * @param asunto       línea de asunto
 * @param texto        cuerpo en texto plano, sin datos clínicos
 */
public record Mensaje(String destinatario, String asunto, String texto) {
}
