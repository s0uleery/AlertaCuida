package cl.ucn.alertacuida.application.telemetria.puerto;

import cl.ucn.alertacuida.application.telemetria.modelo.EventoDescartado;
import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRecibida;
import cl.ucn.alertacuida.application.telemetria.modelo.LoteRecibido;
import cl.ucn.alertacuida.application.telemetria.modelo.SincronizacionGuardada;
import cl.ucn.alertacuida.application.telemetria.modelo.UbicacionRecibida;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lotes del reloj y lo que traen (tablas sincronizacion, registro_vital, ubicacion y
 * evento_descartado). Lo repetido se ignora en la base, sin error.
 */
public interface Sincronizaciones {

	/** @return el lote con ese eventId, si ya llegó */
	Optional<SincronizacionGuardada> buscar(UUID eventId);

	/**
	 * Guarda el encabezado del lote (estado del reloj).
	 *
	 * @return su identificador, o vacío si otro lote con el mismo eventId llegó antes
	 */
	Optional<UUID> crear(UUID idAsignacion, LoteRecibido lote, Instant recepcion);

	/**
	 * @return cuántas se guardaron; las que ya estaban (mismo tipo y hora) no se guardan otra vez
	 */
	int guardarLecturas(UUID idSincronizacion, UUID idAsignacion, List<LecturaRecibida> lecturas);

	/** Guarda las ubicaciones; las que ya estaban (misma hora) no se guardan otra vez. */
	void guardarUbicaciones(UUID idSincronizacion, UUID idAsignacion, List<UbicacionRecibida> ubicaciones);

	/** Guarda los contadores, uno por tipo de evento. */
	void guardarDescartados(UUID idSincronizacion, List<EventoDescartado> descartados);

}
