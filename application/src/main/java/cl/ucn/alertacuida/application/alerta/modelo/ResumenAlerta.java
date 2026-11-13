package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.EstadoAtencion;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.UUID;

/**
 * Una fila de la lista de alertas.
 *
 * @param numero          folio legible
 * @param severidad       la actual: GRAVE si se agravó
 * @param responsable     nombre de quien tomó el caso; null si nadie
 * @param prioridadMaxima si nadie de la lista confirmó y la alerta sigue abierta
 */
public record ResumenAlerta(UUID id, long numero, PacienteDeAlerta paciente, TipoEvento tipoEvento,
		Severidad severidad, EstadoContacto estadoContacto, EstadoAtencion estadoAtencion, String responsable,
		Instant recepcion, boolean prioridadMaxima) {
}
