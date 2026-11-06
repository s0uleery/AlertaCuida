package cl.ucn.alertacuida.application.alerta.puerto;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.Discrepancia;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Alertas guardadas (tablas alerta y discrepancia_alerta) y su estado (vista alerta_estado). */
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

	/** Guarda lo que no calzó con la configuración vigente, una fila por discrepancia. */
	void guardarDiscrepancias(UUID idAlerta, List<Discrepancia> discrepancias);

	/** @return cuántas alertas del reloj (sin contar las canceladas) llegaron por ese vínculo desde esa hora */
	int contarRecientes(UUID idAsignacion, Instant desde);

	/**
	 * Crea una alerta técnica del servidor, salvo que ya haya una abierta (sin cierre del equipo) del
	 * mismo vínculo y tipo. Debe llamarse dentro de una transacción: usa un candado de la base para
	 * que dos llamadas a la vez no creen dos.
	 *
	 * @param detalle se guarda como evidencia (texto JSON)
	 * @return si la creó
	 */
	boolean crearTecnicaSiNoHayAbierta(UUID idAsignacion, TipoEvento tipo, Severidad severidad, String detalle,
			Instant ahora);

	/**
	 * Registra que una caída pasó a grave. No cambia una hora ya registrada.
	 *
	 * @param segundosInmovilidad cuánto tiempo quedó sin moverse; puede ser null
	 * @return si la registró ahora (false si ya estaba agravada)
	 */
	boolean agravar(UUID idAlerta, Instant fecha, Integer segundosInmovilidad);

}
