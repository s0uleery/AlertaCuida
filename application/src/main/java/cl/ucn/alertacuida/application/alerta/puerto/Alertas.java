package cl.ucn.alertacuida.application.alerta.puerto;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.Discrepancia;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Alertas guardadas (tablas alerta y discrepancia_alerta) y su estado (vista alerta_estado). La búsqueda
 * por eventId y el estado vienen de {@link AlertasDelReloj}.
 */
public interface Alertas extends AlertasDelReloj {

	/**
	 * Guarda una alerta que envió el reloj.
	 *
	 * @return la alerta guardada, o vacío si otra con el mismo eventId llegó antes
	 */
	Optional<AlertaGuardada> crear(UUID idAsignacion, AlertaRecibida alerta, Instant recepcion);

	/** Guarda lo que no calzó con la configuración vigente, una fila por discrepancia. */
	void guardarDiscrepancias(UUID idAlerta, List<Discrepancia> discrepancias);

	/** @return cuántas alertas del reloj (sin contar las canceladas) llegaron por ese vínculo desde esa hora */
	int contarRecientes(UUID idAsignacion, Instant desde);

	/**
	 * Crea una alerta técnica del servidor, salvo que ya haya una abierta (sin cierre del equipo) del
	 * mismo vínculo y tipo. Debe llamarse dentro de una transacción: usa un candado de la base para
	 * que dos llamadas a la vez no creen dos.
	 *
	 * @param detalle        se guarda como evidencia (texto JSON)
	 * @param esperaDiaHabil si queda pendiente para el día hábil (solo las leves fuera de horario)
	 * @return si la creó
	 */
	boolean crearTecnicaSiNoHayAbierta(UUID idAsignacion, TipoEvento tipo, Severidad severidad, String detalle,
			boolean esperaDiaHabil, Instant ahora);

	/**
	 * @return cuándo se creó la última alerta técnica del servidor de ese vínculo y tipo, solo si esa
	 *         última la cerró una persona; vacío si no hay, si sigue abierta o si la cerró el sistema
	 */
	Optional<Instant> ultimaCerradaPorPersona(UUID idAsignacion, TipoEvento tipo);

	/**
	 * Cierra, sin responsable, la alerta técnica abierta de ese vínculo y tipo que creó el servidor. Los
	 * simulacros no se tocan.
	 *
	 * @return si había una abierta y la cerró
	 */
	boolean cerrarTecnicaAbierta(UUID idAsignacion, TipoEvento tipo, Instant ahora);

	/**
	 * Registra que una caída pasó a grave. No cambia una hora ya registrada.
	 *
	 * @param segundosInmovilidad cuánto tiempo quedó sin moverse; puede ser null
	 * @return si la registró ahora (false si ya estaba agravada)
	 */
	boolean agravar(UUID idAlerta, Instant fecha, Integer segundosInmovilidad);

	/** Registra que el reloj terminó la lista sin confirmación. No cambia una hora ya registrada. */
	void registrarSinRespuesta(UUID idAlerta, Instant fecha);

}
