package cl.ucn.alertacuida.application.escalamiento.modelo;

import java.util.UUID;

/**
 * Un aviso ya registrado con cierto eventId.
 *
 * @param idIntento su fila en registro_intento
 * @param idAlerta  la alerta a la que pertenece
 */
public record IntentoExistente(UUID idIntento, UUID idAlerta) {
}
