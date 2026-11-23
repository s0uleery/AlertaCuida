package cl.ucn.alertacuida.application.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.Discrepancia;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.LimiteAlertas;
import cl.ucn.alertacuida.application.alerta.modelo.RespuestaAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.ResultadoAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.calendario.ReglaEnrutamiento;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Alertas que envía el reloj: las que avisa, las que el paciente canceló en los 30 segundos, el
 * agravamiento de una caída y la consulta de su estado.
 *
 * <p>El servidor nunca descarta una alerta por su contenido: solo rechaza un mensaje mal formado.
 * Reenviarla con el mismo eventId devuelve la misma respuesta, sin duplicarla. Lo que no calza con
 * la configuración vigente queda como discrepancia, y un reloj que envía demasiadas alertas genera
 * una alerta técnica EXCESO_ALERTAS, sin rechazar ninguna.
 */
public class RecibirAlerta {

	/** Gravedad de la alerta técnica por exceso de alertas: se revisa el reloj en horario hábil. */
	static final Severidad SEVERIDAD_EXCESO = Severidad.LEVE;

	private final Alertas alertas;
	private final RevisionDiscrepancias revision;
	private final LimiteAlertas limite;
	private final ReglaEnrutamiento enrutamiento;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionApiReloj.
	 *
	 * @param revision     compara cada alerta nueva con la configuración vigente
	 * @param limite       cuántas alertas por ventana antes de avisar del exceso
	 * @param enrutamiento decide si la alerta técnica de exceso espera al día hábil
	 */
	public RecibirAlerta(Alertas alertas, RevisionDiscrepancias revision, LimiteAlertas limite,
			ReglaEnrutamiento enrutamiento, Transacciones transacciones, Clock reloj) {
		this.alertas = alertas;
		this.revision = revision;
		this.limite = limite;
		this.enrutamiento = enrutamiento;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Una alerta que terminó su ventana de 30 segundos sin cancelarse.
	 *
	 * <p>Solo una leve puede esperar al día hábil: si llega una media o grave marcada así, se guarda
	 * sin la marca, porque esas avisan siempre. Del mismo modo, el botón de ayuda siempre es leve: si
	 * llega con otra gravedad se guarda como leve, en vez de rechazar un pedido de ayuda.
	 *
	 * @param quien  el reloj que la envía, según su token
	 * @param alerta lo que envió (sin hora de cancelación)
	 * @return la alerta y su estado
	 * @throws SolicitudRechazada si falta un dato o el tipo solo lo detecta el servidor
	 * @throws Conflicto          si el eventId ya lo usó otro reloj o una cancelación
	 */
	public ResultadoAlerta recibir(RelojAutenticado quien, AlertaRecibida alerta) {
		if (alerta.envio() == null) {
			throw new SolicitudRechazada("Falta fechaHoraEnvio");
		}
		Severidad severidad = severidadValida(alerta);
		boolean espera = alerta.esperaDiaHabil() && severidad == Severidad.LEVE;
		return guardar(quien, alerta.con(severidad, espera));
	}

	/**
	 * Una alerta que el paciente canceló en los 30 segundos. Se guarda solo para las métricas:
	 * queda CANCELADA, sin avisos ni atención, y no se compara con la configuración.
	 *
	 * @param quien       el reloj que la envía, según su token
	 * @param cancelacion lo que envió, con la hora de cancelación
	 * @return la alerta cancelada y su estado
	 * @throws SolicitudRechazada si falta un dato o el tipo solo lo detecta el servidor
	 * @throws Conflicto          si el eventId ya lo usó otro reloj o una alerta que sí se envió
	 */
	public ResultadoAlerta registrarCancelacion(RelojAutenticado quien, AlertaRecibida cancelacion) {
		if (cancelacion.cancelacion() == null) {
			throw new SolicitudRechazada("Falta fechaHoraCancelacion");
		}
		return guardar(quien, cancelacion.con(severidadValida(cancelacion), false));
	}

	/**
	 * Una caída leve o media que pasa a grave porque la persona sigue sin moverse. Repetirlo no
	 * cambia la hora ya registrada.
	 *
	 * @param quien               el reloj que lo envía, según su token
	 * @param eventId             el de la caída
	 * @param fecha               cuándo pasó a grave
	 * @param segundosInmovilidad cuánto tiempo quedó sin moverse; puede ser null
	 * @return el estado de la alerta, ya grave
	 * @throws NoEncontrado       si no hay una alerta con ese eventId de este reloj
	 * @throws Conflicto          si no es una caída, llegó grave o la canceló el paciente
	 * @throws SolicitudRechazada si falta la hora o la inmovilidad es negativa
	 */
	public EstadoAlerta agravar(RelojAutenticado quien, UUID eventId, Instant fecha, Integer segundosInmovilidad) {
		if (fecha == null) {
			throw new SolicitudRechazada("Falta fechaHoraAgravamiento");
		}
		if (segundosInmovilidad != null && segundosInmovilidad < 0) {
			throw new SolicitudRechazada("segundosInmovilidad no puede ser negativo");
		}
		AlertaGuardada alerta = deEsteReloj(quien, eventId);
		if (!alerta.agravada()) {
			if (alerta.tipo() != TipoEvento.CAIDA) {
				throw new Conflicto("Solo una caída puede agravarse");
			}
			if (alerta.severidad() == Severidad.GRAVE) {
				throw new Conflicto("La caída ya llegó grave");
			}
			if (alerta.cancelada()) {
				throw new Conflicto("El paciente canceló esa alerta");
			}
			alertas.agravar(alerta.idAlerta(), fecha, segundosInmovilidad);
		}
		return alertas.estado(alerta.idAlerta());
	}

	/**
	 * El reloj recorrió la lista dos veces sin confirmación. Repetirlo no cambia la hora ya registrada.
	 *
	 * @param quien   el reloj que lo informa, según su token
	 * @param eventId el de la alerta
	 * @param fecha   cuándo terminó la lista
	 * @return el estado de la alerta
	 * @throws NoEncontrado       si no hay una alerta con ese eventId de este reloj
	 * @throws Conflicto          si la canceló el paciente
	 * @throws SolicitudRechazada si falta la hora
	 */
	public EstadoAlerta informarSinRespuesta(RelojAutenticado quien, UUID eventId, Instant fecha) {
		if (fecha == null) {
			throw new SolicitudRechazada("Falta fechaHora");
		}
		AlertaGuardada alerta = deEsteReloj(quien, eventId);
		if (alerta.cancelada()) {
			throw new Conflicto("El paciente canceló esa alerta");
		}
		alertas.registrarSinRespuesta(alerta.idAlerta(), fecha);
		return alertas.estado(alerta.idAlerta());
	}

	/**
	 * @param quien   el reloj que pregunta, según su token
	 * @param eventId el de la alerta
	 * @return su estado actual
	 * @throws NoEncontrado si no hay una alerta con ese eventId de este reloj
	 */
	public EstadoAlerta estado(RelojAutenticado quien, UUID eventId) {
		return alertas.estado(deEsteReloj(quien, eventId).idAlerta());
	}

	/** @throws NoEncontrado si no hay una alerta con ese eventId de este reloj */
	private AlertaGuardada deEsteReloj(RelojAutenticado quien, UUID eventId) {
		return alertas.buscar(eventId)
				.filter(a -> a.idAsignacion().equals(quien.idAsignacion()))
				.orElseThrow(() -> new NoEncontrado("No hay una alerta con ese eventId para este reloj"));
	}

	/**
	 * Guarda la alerta, o devuelve la que ya estaba si el reloj la reenvió. Una alerta nueva (no
	 * cancelada) se guarda con sus discrepancias y, si el reloj pasó el límite, con la alerta técnica,
	 * todo en una transacción.
	 */
	private ResultadoAlerta guardar(RelojAutenticado quien, AlertaRecibida alerta) {
		validar(alerta);
		boolean cancelada = alerta.cancelacion() != null;
		Optional<AlertaGuardada> anterior = alertas.buscar(alerta.eventId());
		if (anterior.isPresent()) {
			return new ResultadoAlerta(respuesta(repetida(anterior.get(), quien, cancelada)), false);
		}
		List<Discrepancia> discrepancias = cancelada ? List.of() : revisarSinFallar(quien.idPaciente(), alerta);
		Instant ahora = reloj.instant();
		Optional<AlertaGuardada> nueva = transacciones.ejecutar(() -> {
			Optional<AlertaGuardada> creada = alertas.crear(quien.idAsignacion(), alerta, ahora);
			if (creada.isPresent() && !cancelada) {
				alertas.guardarDiscrepancias(creada.get().idAlerta(), discrepancias);
				vigilarExceso(quien.idAsignacion(), ahora);
			}
			return creada;
		});
		if (nueva.isEmpty()) {
			// Otro envío de la misma alerta se guardó entre la búsqueda y el INSERT
			AlertaGuardada otra = alertas.buscar(alerta.eventId()).orElseThrow();
			return new ResultadoAlerta(respuesta(repetida(otra, quien, cancelada)), false);
		}
		return new ResultadoAlerta(respuesta(nueva.get()), true);
	}

	/**
	 * @return las discrepancias de la alerta; vacía si la revisión misma falla, porque guardar la
	 *         alerta importa más que revisarla
	 */
	private List<Discrepancia> revisarSinFallar(UUID idPaciente, AlertaRecibida alerta) {
		try {
			return revision.revisar(idPaciente, alerta);
		} catch (RuntimeException fallaDeLaRevision) {
			return List.of();
		}
	}

	/** Si el reloj pasó el límite de alertas en la ventana, crea la alerta técnica (una abierta a la vez). */
	private void vigilarExceso(UUID idAsignacion, Instant ahora) {
		int recientes = alertas.contarRecientes(idAsignacion, ahora.minus(limite.ventana()));
		if (recientes >= limite.avisarDesde()) {
			alertas.crearTecnicaSiNoHayAbierta(idAsignacion, TipoEvento.EXCESO_ALERTAS, SEVERIDAD_EXCESO,
					"{\"alertas\": " + recientes + ", \"ventanaMinutos\": " + limite.ventana().toMinutes() + "}",
					enrutamiento.esperaDiaHabil(SEVERIDAD_EXCESO, ahora), ahora);
		}
	}

	/** @return la gravedad enviada, salvo en el botón de ayuda, que siempre es leve (regla alerta_boton_leve) */
	private static Severidad severidadValida(AlertaRecibida alerta) {
		return alerta.tipo() == TipoEvento.BOTON_AYUDA ? Severidad.LEVE : alerta.severidad();
	}

	/** Lo que la base exige a toda alerta del reloj. */
	private static void validar(AlertaRecibida alerta) {
		if (!alerta.tipo().loDetectaElReloj()) {
			throw new SolicitudRechazada("El reloj no envía alertas de " + alerta.tipo());
		}
		if (alerta.versionConfiguracion() == null || alerta.versionConfiguracion().isBlank()) {
			throw new SolicitudRechazada("Falta versionConfiguracion");
		}
	}

	/**
	 * @return la alerta guardada, si de verdad es la misma que se reenvió
	 * @throws Conflicto si es de otro reloj, o si una es alerta y la otra cancelación
	 */
	private static AlertaGuardada repetida(AlertaGuardada anterior, RelojAutenticado quien, boolean cancelada) {
		if (!anterior.idAsignacion().equals(quien.idAsignacion())) {
			throw new Conflicto("Ese eventId ya lo usó otro reloj");
		}
		if (anterior.cancelada() != cancelada) {
			throw new Conflicto(cancelada ? "Esa alerta ya se envió: no se puede registrar como cancelada"
					: "Esa alerta se registró como cancelada");
		}
		return anterior;
	}

	/** @return la alerta con su estado calculado */
	private RespuestaAlerta respuesta(AlertaGuardada alerta) {
		return new RespuestaAlerta(alerta.idAlerta(), alerta.numero(), alertas.estado(alerta.idAlerta()));
	}

}
