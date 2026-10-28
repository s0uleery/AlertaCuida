package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.telemetria.modelo.EventoDescartado;
import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRecibida;
import cl.ucn.alertacuida.application.telemetria.modelo.LoteRecibido;
import cl.ucn.alertacuida.application.telemetria.modelo.SincronizacionGuardada;
import cl.ucn.alertacuida.application.telemetria.modelo.UbicacionRecibida;
import cl.ucn.alertacuida.application.telemetria.modelo.VersionInformada;
import cl.ucn.alertacuida.application.telemetria.puerto.Sincronizaciones;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Doble de {@link Sincronizaciones} para las pruebas unitarias. Igual que la base, ignora un lote
 * con un eventId ya guardado y una lectura con la misma asignación, tipo y hora.
 */
public final class SincronizacionesEnMemoria implements Sincronizaciones {

	/** Un lote guardado, con la versión de configuración que informó el reloj. */
	public record Fila(UUID id, UUID eventId, UUID idAsignacion, Instant recepcion, String version) {
	}

	/** Lotes guardados, por su identificador. */
	public final Map<UUID, Fila> lotes = new LinkedHashMap<>();
	/** Lecturas nuevas de cada lote, por el identificador del lote. */
	public final Map<UUID, List<LecturaRecibida>> lecturasPorLote = new LinkedHashMap<>();
	/** Todas las ubicaciones guardadas. */
	public final List<UbicacionRecibida> ubicaciones = new ArrayList<>();
	/** Todos los contadores guardados. */
	public final List<EventoDescartado> descartados = new ArrayList<>();
	/** "asignación|tipo|hora" de cada lectura guardada, como el UNIQUE de registro_vital. */
	private final Set<String> clavesLecturas = new HashSet<>();

	@Override
	public Optional<SincronizacionGuardada> buscar(UUID eventId) {
		return lotes.values().stream().filter(f -> f.eventId().equals(eventId)).findFirst()
				.map(f -> new SincronizacionGuardada(f.id(), f.idAsignacion(), f.recepcion(),
						lecturasPorLote.getOrDefault(f.id(), List.of()).size()));
	}

	@Override
	public Optional<UUID> crear(UUID idAsignacion, LoteRecibido lote, Instant recepcion) {
		if (buscar(lote.eventId()).isPresent()) {
			return Optional.empty();
		}
		UUID id = UUID.randomUUID();
		lotes.put(id, new Fila(id, lote.eventId(), idAsignacion, recepcion, lote.estado().versionConfiguracion()));
		return Optional.of(id);
	}

	@Override
	public int guardarLecturas(UUID idSincronizacion, UUID idAsignacion, List<LecturaRecibida> lecturas) {
		List<LecturaRecibida> nuevas = lecturas.stream()
				.filter(l -> clavesLecturas.add(idAsignacion + "|" + l.tipo() + "|" + l.medicion()))
				.toList();
		lecturasPorLote.computeIfAbsent(idSincronizacion, id -> new ArrayList<>()).addAll(nuevas);
		return nuevas.size();
	}

	@Override
	public void guardarUbicaciones(UUID idSincronizacion, UUID idAsignacion, List<UbicacionRecibida> nuevas) {
		ubicaciones.addAll(nuevas);
	}

	@Override
	public void guardarDescartados(UUID idSincronizacion, List<EventoDescartado> nuevos) {
		descartados.addAll(nuevos);
	}

	/** Igual que la base: "desde" cuenta desde el primer lote después del último con otra versión. */
	@Override
	public Optional<VersionInformada> versionInformada(UUID idAsignacion) {
		List<Fila> delReloj = lotes.values().stream().filter(f -> f.idAsignacion().equals(idAsignacion))
				.sorted(Comparator.comparing(Fila::recepcion)).toList();
		if (delReloj.isEmpty()) {
			return Optional.empty();
		}
		Fila ultima = delReloj.getLast();
		Instant desde = ultima.recepcion();
		for (int i = delReloj.size() - 1; i >= 0 && Objects.equals(delReloj.get(i).version(), ultima.version()); i--) {
			desde = delReloj.get(i).recepcion();
		}
		return Optional.of(new VersionInformada(ultima.version(), ultima.recepcion(), desde));
	}

}
