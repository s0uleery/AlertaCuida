package cl.ucn.alertacuida.application.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.RespuestaAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.ResultadoAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Alertas que envía el reloj: las que avisa, las que el paciente canceló en los 30 segundos y la
 * consulta de su estado.
 *
 * <p>El servidor nunca descarta una alerta por su contenido: solo rechaza un mensaje mal formado.
 * Reenviarla con el mismo eventId devuelve la misma respuesta, sin duplicarla.
 */
public class RecibirAlerta {

	private final Alertas alertas;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionApiReloj. */
	public RecibirAlerta(Alertas alertas, Clock reloj) {
		this.alertas = alertas;
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
		return guardar(quien, new AlertaRecibida(alerta.eventId(), alerta.tipo(), severidad,
				alerta.versionConfiguracion(), alerta.deteccion(), alerta.envio(), espera, null, alerta.evidencia()));
	}

	/**
	 * Una alerta que el paciente canceló en los 30 segundos. Se guarda solo para las métricas:
	 * queda CANCELADA, sin avisos ni atención.
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
		return guardar(quien, new AlertaRecibida(cancelacion.eventId(), cancelacion.tipo(),
				severidadValida(cancelacion),
				cancelacion.versionConfiguracion(), cancelacion.deteccion(), null, false, cancelacion.cancelacion(),
				cancelacion.evidencia()));
	}

	/**
	 * @param quien   el reloj que pregunta, según su token
	 * @param eventId el de la alerta
	 * @return su estado actual
	 * @throws NoEncontrado si no hay una alerta con ese eventId de este reloj
	 */
	public EstadoAlerta estado(RelojAutenticado quien, UUID eventId) {
		AlertaGuardada guardada = alertas.buscar(eventId)
				.filter(a -> a.idAsignacion().equals(quien.idAsignacion()))
				.orElseThrow(() -> new NoEncontrado("No hay una alerta con ese eventId para este reloj"));
		return alertas.estado(guardada.idAlerta());
	}

	/** Guarda la alerta, o devuelve la que ya estaba si el reloj la reenvió. */
	private ResultadoAlerta guardar(RelojAutenticado quien, AlertaRecibida alerta) {
		validar(alerta);
		boolean cancelada = alerta.cancelacion() != null;
		Optional<AlertaGuardada> anterior = alertas.buscar(alerta.eventId());
		if (anterior.isPresent()) {
			return new ResultadoAlerta(respuesta(repetida(anterior.get(), quien, cancelada)), false);
		}
		Optional<AlertaGuardada> nueva = alertas.crear(quien.idAsignacion(), alerta, reloj.instant());
		if (nueva.isEmpty()) {
			// Otro envío de la misma alerta se guardó entre la búsqueda y el INSERT
			AlertaGuardada otra = alertas.buscar(alerta.eventId()).orElseThrow();
			return new ResultadoAlerta(respuesta(repetida(otra, quien, cancelada)), false);
		}
		return new ResultadoAlerta(respuesta(nueva.get()), true);
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
