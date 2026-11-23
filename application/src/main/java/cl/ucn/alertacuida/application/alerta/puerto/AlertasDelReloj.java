package cl.ucn.alertacuida.application.alerta.puerto;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo mínimo para atender a un reloj que pregunta por su alerta: buscarla por su eventId y leer su estado.
 * Va aparte de {@link Alertas} para que quien solo consulta no dependa de todo lo demás.
 */
public interface AlertasDelReloj {

	/** @return la alerta con ese eventId, si ya llegó */
	Optional<AlertaGuardada> buscar(UUID eventId);

	/** @return el estado calculado de una alerta que existe */
	EstadoAlerta estado(UUID idAlerta);

}
