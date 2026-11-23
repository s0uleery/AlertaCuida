package cl.ucn.alertacuida.application.panel.modelo;

import java.util.UUID;

/**
 * Una fila de la portada.
 *
 * @param reloj           su reloj vigente; null si no tiene
 * @param alertasAbiertas sin cerrar por el equipo, sin canceladas ni simulacros
 */
public record UsuarioPanel(UUID id, String nombres, String apellidos, RelojPanel reloj, int alertasAbiertas) {
}
