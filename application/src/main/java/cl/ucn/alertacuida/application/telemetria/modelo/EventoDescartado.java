package cl.ucn.alertacuida.application.telemetria.modelo;

import cl.ucn.alertacuida.domain.alerta.TipoEvento;

/** Cuántas veces el reloj detectó un evento sin alertarlo desde el lote anterior (métricas). */
public record EventoDescartado(TipoEvento tipo, int cantidad) {
}
