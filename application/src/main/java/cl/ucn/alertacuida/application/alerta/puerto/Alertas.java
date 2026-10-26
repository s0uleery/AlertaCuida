package cl.ucn.alertacuida.application.alerta.puerto;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Alertas guardadas (tabla alerta) y su estado (vista alerta_estado). */
public interface Alertas {

	/** @return la alerta con ese eventId, si ya llegó */
	Optional<AlertaGuardada> buscar(UUID eventId);

	/**
	 * Guarda una alerta que envió el reloj.
	 *
	 * @return la alerta guardada, o vacío si otra con el mismo eventId llegó antes
	 */
	Optional<AlertaGuardada> crear(UUID idAsignacion, AlertaRecibida alerta, Instant recepcion);

	/** @return el estado calculado de una alerta que existe */
	EstadoAlerta estado(UUID idAlerta);

}
