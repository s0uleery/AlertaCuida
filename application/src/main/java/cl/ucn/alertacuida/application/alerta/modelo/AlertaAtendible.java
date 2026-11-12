package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.UUID;

/**
 * Lo que hace falta saber de una alerta para atenderla.
 *
 * @param delReloj  si la envió el reloj; las del servidor no pasan por su escalamiento
 * @param cancelada si la canceló el paciente
 */
public record AlertaAtendible(UUID id, TipoEvento tipo, boolean delReloj, boolean cancelada) {
}
