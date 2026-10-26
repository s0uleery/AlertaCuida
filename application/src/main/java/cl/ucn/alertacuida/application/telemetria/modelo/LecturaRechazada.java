package cl.ucn.alertacuida.application.telemetria.modelo;

/**
 * Una lectura que no se guardó.
 *
 * @param indice su posición en la lista que envió el reloj, desde 0
 */
public record LecturaRechazada(int indice, MotivoRechazo motivo) {
}
