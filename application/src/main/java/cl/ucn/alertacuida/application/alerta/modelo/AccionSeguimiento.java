package cl.ucn.alertacuida.application.alerta.modelo;

import java.time.Instant;

/**
 * Una nota o acción del equipo sobre un caso.
 *
 * @param tipo    LLAMADA, VISITA, DERIVACION o NOTA
 * @param destino a dónde se derivó; null si no es una derivación
 * @param nota    texto ya descifrado; puede ser null
 * @param usuario nombre de quien la registró
 */
public record AccionSeguimiento(String tipo, String destino, String nota, String usuario, Instant fechaHora) {
}
