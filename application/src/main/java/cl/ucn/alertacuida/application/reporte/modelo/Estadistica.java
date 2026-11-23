package cl.ucn.alertacuida.application.reporte.modelo;

/**
 * Resumen de una variable en el período. Sin lecturas, promedio, mínimo y máximo van vacíos.
 *
 * @param promedio redondeado a un decimal
 * @param lecturas cuántas hubo
 */
public record Estadistica(Double promedio, Double minimo, Double maximo, int lecturas) {
}
