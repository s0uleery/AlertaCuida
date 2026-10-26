package cl.ucn.alertacuida.application.alerta.modelo;

import java.util.UUID;

/**
 * Lo que se responde al reloj al recibir una alerta o una cancelación.
 *
 * @param numero folio legible para el equipo (#142)
 */
public record RespuestaAlerta(UUID idAlerta, long numero, EstadoAlerta estado) {
}
