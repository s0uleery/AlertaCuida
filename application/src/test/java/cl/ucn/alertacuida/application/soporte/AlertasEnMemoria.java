package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.Discrepancia;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de {@link Alertas} para las pruebas unitarias. El estado es una versión reducida de la
 * vista alerta_estado: sin avisos ni atención, solo cancelada, pendiente de día hábil o recibida;
 * la gravedad es GRAVE si se agravó.
 */
public final class AlertasEnMemoria implements Alertas {

	/**
	 * Una alerta guardada y lo que se sabe de ella.
	 *
	 * @param recepcion   cuándo llegó
	 * @param agravamiento cuándo pasó a grave, o null
	 * @param inmovilidad  segundos sin moverse al agravarse, o null
	 */
	public record Fila(AlertaGuardada guardada, AlertaRecibida alerta, Instant recepcion, Instant agravamiento,
			Integer inmovilidad) {
	}

	/** Alertas del reloj guardadas, por su eventId. */
	public final Map<UUID, Fila> filas = new LinkedHashMap<>();
	/** Discrepancias guardadas, por id de alerta. */
	public final Map<UUID, List<Discrepancia>> discrepancias = new HashMap<>();
	/** Alertas técnicas abiertas creadas por el servidor, como "tipo|vínculo". */
	public final List<String> tecnicas = new ArrayList<>();
	/** Alertas técnicas que el sistema cerró, como "tipo|vínculo". */
	public final List<String> cerradas = new ArrayList<>();
	/** Gravedad con que se creó cada alerta técnica, por "tipo|vínculo". */
	public final Map<String, Severidad> gravedades = new HashMap<>();
	/** Si cada alerta técnica quedó pendiente para el día hábil, por "tipo|vínculo". */
	public final Map<String, Boolean> esperas = new HashMap<>();
	/** Cuándo se creó la última alerta técnica, si la cerró una persona, por "tipo|vínculo". */
	public final Map<String, Instant> cerradasPorPersona = new HashMap<>();
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
		AlertaGuardada guardada = new AlertaGuardada(UUID.randomUUID(), idAsignacion, ++folio, alerta.tipo(),
				alerta.severidad(), alerta.cancelacion() != null, false);
		filas.put(alerta.eventId(), new Fila(guardada, alerta, recepcion, null, null));
		return Optional.of(guardada);
	}

	@Override
	public EstadoAlerta estado(UUID idAlerta) {
		Fila fila = porId(idAlerta);
		AlertaRecibida alerta = fila.alerta();
		EstadoContacto estado = alerta.cancelacion() != null ? EstadoContacto.CANCELADA
				: alerta.esperaDiaHabil() ? EstadoContacto.PENDIENTE_DIA_HABIL : EstadoContacto.RECIBIDA;
		Severidad actual = fila.agravamiento() != null ? Severidad.GRAVE : alerta.severidad();
		return EstadoAlerta.de(alerta.eventId(), estado, actual, null);
	}

	@Override
	public void guardarDiscrepancias(UUID idAlerta, List<Discrepancia> encontradas) {
		discrepancias.put(idAlerta, List.copyOf(encontradas));
	}

	@Override
	public int contarRecientes(UUID idAsignacion, Instant desde) {
		return (int) filas.values().stream()
				.filter(f -> f.guardada().idAsignacion().equals(idAsignacion) && !f.guardada().cancelada()
						&& f.recepcion().isAfter(desde))
				.count();
	}

	@Override
	public boolean crearTecnicaSiNoHayAbierta(UUID idAsignacion, TipoEvento tipo, Severidad severidad,
			String detalle, boolean esperaDiaHabil, Instant ahora) {
		String clave = tipo + "|" + idAsignacion;
		if (tecnicas.contains(clave)) {
			return false;
		}
		tecnicas.add(clave);
		gravedades.put(clave, severidad);
		esperas.put(clave, esperaDiaHabil);
		return true;
	}

	@Override
	public Optional<Instant> ultimaCerradaPorPersona(UUID idAsignacion, TipoEvento tipo) {
		return Optional.ofNullable(cerradasPorPersona.get(tipo + "|" + idAsignacion));
	}

	@Override
	public boolean cerrarTecnicaAbierta(UUID idAsignacion, TipoEvento tipo, Instant ahora) {
		String clave = tipo + "|" + idAsignacion;
		boolean habia = tecnicas.remove(clave);
		if (habia) {
			cerradas.add(clave);
		}
		return habia;
	}

	@Override
	public boolean agravar(UUID idAlerta, Instant fecha, Integer segundosInmovilidad) {
		Fila fila = porId(idAlerta);
		if (fila.agravamiento() != null) {
			return false;
		}
		AlertaGuardada g = fila.guardada();
		filas.put(fila.alerta().eventId(), new Fila(new AlertaGuardada(g.idAlerta(), g.idAsignacion(), g.numero(),
				g.tipo(), g.severidad(), g.cancelada(), true), fila.alerta(), fila.recepcion(), fecha,
				segundosInmovilidad));
		return true;
	}

	/** @return la fila de esa alerta */
	private Fila porId(UUID idAlerta) {
		return filas.values().stream().filter(f -> f.guardada().idAlerta().equals(idAlerta)).findFirst()
				.orElseThrow();
	}

}
