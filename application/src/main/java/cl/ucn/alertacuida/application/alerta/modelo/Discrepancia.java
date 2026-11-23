package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.CodigoDiscrepancia;

/**
 * Una discrepancia de una alerta (una fila de discrepancia_alerta).
 *
 * @param codigo  qué no calzó
 * @param detalle explicación legible para el equipo, ej. "FC 95 dentro del rango normal 50–110"
 */
public record Discrepancia(CodigoDiscrepancia codigo, String detalle) {
}
