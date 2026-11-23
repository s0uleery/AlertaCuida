package cl.ucn.alertacuida.application.panel.modelo;

import java.util.UUID;

/**
 * Un usuario activo tal como se lee de la base, con sus datos ya descifrados.
 *
 * @param idDispositivo      su reloj vigente; null si no tiene
 * @param identificadorReloj el identificador de ese reloj; null si no tiene
 * @param alertasAbiertas    sin cerrar por el equipo, sin canceladas ni simulacros
 */
public record FilaPanel(UUID id, String nombres, String apellidos, UUID idDispositivo, String identificadorReloj,
		int alertasAbiertas) {
}
