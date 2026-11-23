package cl.ucn.alertacuida.application.tendencia.modelo;

import java.time.Instant;

/**
 * Un punto del gráfico. Con {@link Agrupacion#LECTURA} es una sola lectura: promedio, mínimo y máximo son su
 * valor y la cantidad es 1. Así el gráfico recibe siempre el mismo formato.
 *
 * @param fechaHora la hora de medición, o el comienzo de la hora o del día de Chile
 * @param cantidad  cuántas lecturas junta
 */
public record PuntoTendencia(Instant fechaHora, double promedio, double minimo, double maximo, int cantidad) {
}
