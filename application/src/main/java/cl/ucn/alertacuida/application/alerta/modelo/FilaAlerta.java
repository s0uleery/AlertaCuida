package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.EstadoAtencion;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una alerta tal como se lee de la base, con los datos de la persona ya descifrados.
 *
 * @param nacimiento null si no se conoce
 * @param severidad  la actual, de la vista alerta_estado
 * @param responsable nombre de quien tomó el caso; null si nadie
 */
public record FilaAlerta(UUID id, long numero, UUID idPaciente, String nombres, String apellidos,
		LocalDate nacimiento, TipoEvento tipoEvento, Severidad severidad, EstadoContacto estadoContacto,
		EstadoAtencion estadoAtencion, String responsable, Instant recepcion) {
}
