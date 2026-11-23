package cl.ucn.alertacuida.application.tendencia.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.UUID;

/**
 * Una alerta para marcar en el gráfico.
 *
 * @param numero             folio legible
 * @param severidad          la de llegada
 * @param fechaHoraDeteccion la hora del reloj al detectarla, la misma escala que las lecturas
 * @param cancelada          si el paciente la canceló en el reloj
 */
public record MarcaAlerta(UUID id, long numero, TipoEvento tipoEvento, Severidad severidad,
		Instant fechaHoraDeteccion, boolean cancelada) {
}
