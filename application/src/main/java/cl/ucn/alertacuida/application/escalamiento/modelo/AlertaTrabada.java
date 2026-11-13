package cl.ucn.alertacuida.application.escalamiento.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Una alerta del reloj que sigue escalando sin que el reloj dé noticias.
 *
 * @param idAlerta      la alerta
 * @param ultimaNoticia lo último que se supo del reloj sobre ella: cuándo llegó, se agravó o pidió su último aviso
 */
public record AlertaTrabada(UUID idAlerta, Instant ultimaNoticia) {
}
