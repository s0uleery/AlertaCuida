package cl.ucn.alertacuida.application.alerta.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * El contacto que respondió "Voy en camino".
 *
 * @param idContacto el vínculo contacto_paciente, el mismo id que el reloj tiene en su configuración
 */
public record ConfirmacionContacto(UUID idContacto, Instant fechaHora) {
}
