package cl.ucn.alertacuida.application.respuesta.modelo;

import java.time.Instant;

/**
 * El último contacto del paciente al que el reloj pidió avisar, para el mensaje "Se avisó a ... a las ..."
 * (N1-06).
 *
 * @param nombre ya descifrado
 * @param envio  cuándo se le envió el aviso
 */
public record ContactoAvisado(String nombre, Instant envio) {
}
