package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de {@link Alertas} para las pruebas unitarias. El estado es una versión reducida de la
 * vista alerta_estado: sin avisos ni atención, solo cancelada, pendiente de día hábil o recibida.
 */
public final class AlertasEnMemoria implements Alertas {

	/** Una alerta guardada y su asignación. */
	public record Fila(AlertaGuardada guardada, AlertaRecibida alerta) {
	}

	/** Alertas guardadas, por su eventId. */
	public final Map<UUID, Fila> filas = new LinkedHashMap<>();
	/** Último folio entregado, como la columna numero. */
	private long folio;

	@Override
	public Optional<AlertaGuardada> buscar(UUID eventId) {
		return Optional.ofNullable(filas.get(eventId)).map(Fila::guardada);
	}

	@Override
	public Optional<AlertaGuardada> crear(UUID idAsignacion, AlertaRecibida alerta, Instant recepcion) {
		if (filas.containsKey(alerta.eventId())) {
			return Optional.empty();
		}
		AlertaGuardada guardada = new AlertaGuardada(UUID.randomUUID(), idAsignacion, ++folio,
				alerta.cancelacion() != null);
		filas.put(alerta.eventId(), new Fila(guardada, alerta));
		return Optional.of(guardada);
	}

	@Override
	public EstadoAlerta estado(UUID idAlerta) {
		AlertaRecibida alerta = filas.values().stream().filter(f -> f.guardada().idAlerta().equals(idAlerta))
				.findFirst().orElseThrow().alerta();
		EstadoContacto estado = alerta.cancelacion() != null ? EstadoContacto.CANCELADA
				: alerta.esperaDiaHabil() ? EstadoContacto.PENDIENTE_DIA_HABIL : EstadoContacto.RECIBIDA;
		return EstadoAlerta.de(alerta.eventId(), estado, alerta.severidad(), null);
	}

}
