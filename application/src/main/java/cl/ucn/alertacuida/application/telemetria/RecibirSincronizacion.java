package cl.ucn.alertacuida.application.telemetria;

import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.configuracion.VersionVigente;
import cl.ucn.alertacuida.application.telemetria.error.LoteGrande;
import cl.ucn.alertacuida.application.telemetria.modelo.EstadoInformado;
import cl.ucn.alertacuida.application.telemetria.modelo.EventoDescartado;
import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRechazada;
import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRecibida;
import cl.ucn.alertacuida.application.telemetria.modelo.LoteRecibido;
import cl.ucn.alertacuida.application.telemetria.modelo.MotivoRechazo;
import cl.ucn.alertacuida.application.telemetria.modelo.ResultadoSincronizacion;
import cl.ucn.alertacuida.application.telemetria.modelo.SincronizacionGuardada;
import cl.ucn.alertacuida.application.telemetria.modelo.UbicacionRecibida;
import cl.ucn.alertacuida.application.telemetria.puerto.Sincronizaciones;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Recibe el lote que el reloj envía cada 2 horas (o al 15 % de batería) y lo guarda.
 *
 * <p>Un mensaje mal formado se rechaza entero (400): es un error del reloj. Pero una lectura mala
 * se descarta sola y se informa, para que la cola del reloj no se trabe reintentando para siempre.
 * Reenviar un lote ya recibido no duplica nada.
 *
 * <p>La respuesta dice si el reloj debe descargar la configuración: si la versión que informa no
 * es la vigente. Así la configuración no viaja en cada sincronización.
 */
public class RecibirSincronizacion {

	/** Máximo de lecturas y de ubicaciones por lote (14 días cada 30 min son unas 1.350 lecturas). */
	static final int MAXIMO_POR_LOTE = 2000;
	/** Margen para relojes un poco adelantados antes de tratar una hora como futura. */
	static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(5);
	/** Desde cuánta diferencia de hora con el servidor se avisa que el reloj está desfasado. */
	static final Duration DESFASE_AVISADO = Duration.ofSeconds(120);

	private final Sincronizaciones sincronizaciones;
	private final VersionVigente versiones;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe lo que usa; lo arma ConfiguracionApiReloj. */
	public RecibirSincronizacion(Sincronizaciones sincronizaciones, VersionVigente versiones,
			Transacciones transacciones, Clock reloj) {
		this.sincronizaciones = sincronizaciones;
		this.versiones = versiones;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param quien el reloj que envía, según su token
	 * @param lote  lo que envió
	 * @return lo guardado, lo repetido y lo descartado
	 * @throws SolicitudRechazada si el estado trae valores imposibles
	 * @throws LoteGrande         si trae más de {@value #MAXIMO_POR_LOTE} lecturas o ubicaciones
	 * @throws Conflicto          si el eventId ya lo usó otro reloj
	 */
	public ResultadoSincronizacion recibir(RelojAutenticado quien, LoteRecibido lote) {
		validar(lote);
		Optional<SincronizacionGuardada> anterior = sincronizaciones.buscar(lote.eventId());
		if (anterior.isPresent()) {
			return repetido(anterior.get(), quien, lote);
		}
		String vigente = versiones.de(quien.idPaciente());
		Instant ahora = reloj.instant();
		List<LecturaRecibida> validas = new ArrayList<>();
		List<LecturaRechazada> rechazadas = new ArrayList<>();
		for (int i = 0; i < lote.lecturas().size(); i++) {
			LecturaRecibida lectura = lote.lecturas().get(i);
			MotivoRechazo motivo = motivoRechazo(lectura, quien, ahora);
			if (motivo == null) {
				validas.add(lectura);
			} else {
				rechazadas.add(new LecturaRechazada(i, motivo));
			}
		}
		// Sin un motivo que informar: una ubicación con hora imposible simplemente no se guarda
		List<UbicacionRecibida> ubicaciones = lote.ubicaciones().stream()
				.filter(u -> horaPosible(u.medicion(), quien, ahora))
				.toList();

		Guardado guardado = transacciones.ejecutar(() -> sincronizaciones.crear(quien.idAsignacion(), lote, ahora)
				.map(id -> {
					int nuevas = sincronizaciones.guardarLecturas(id, quien.idAsignacion(), validas);
					sincronizaciones.guardarUbicaciones(id, quien.idAsignacion(), ubicaciones);
					sincronizaciones.guardarDescartados(id, sumarPorTipo(lote.descartados()));
					return new Guardado(id, nuevas);
				})
				.orElse(null));
		if (guardado == null) {
			// Otro envío del mismo lote se guardó entre la búsqueda y el INSERT
			return repetido(sincronizaciones.buscar(lote.eventId()).orElseThrow(), quien, lote);
		}
		return new ResultadoSincronizacion(guardado.id(), ahora, guardado.lecturas(),
				validas.size() - guardado.lecturas(), rechazadas, false, desfase(lote.envio(), ahora),
				!vigente.equals(lote.estado().versionConfiguracion()), vigente);
	}

	/** Identificador del lote recién guardado y cuántas lecturas nuevas trajo. */
	private record Guardado(UUID id, int lecturas) {
	}

	/** Revisa lo que haría imposible guardar el lote, antes de tocar la base. */
	private static void validar(LoteRecibido lote) {
		if (lote.lecturas().size() > MAXIMO_POR_LOTE || lote.ubicaciones().size() > MAXIMO_POR_LOTE) {
			throw new LoteGrande("Más de " + MAXIMO_POR_LOTE + " lecturas o ubicaciones en un lote");
		}
		EstadoInformado estado = lote.estado();
		if (estado.nivelBateria() < 0 || estado.nivelBateria() > 100) {
			throw new SolicitudRechazada("nivelBateria debe estar entre 0 y 100");
		}
		if (estado.minutosPuesto() != null && estado.minutosPuesto() < 0) {
			throw new SolicitudRechazada("minutosPuesto no puede ser negativo");
		}
		for (EventoDescartado descartado : lote.descartados()) {
			if (!descartado.tipo().loDetectaElReloj()) {
				throw new SolicitudRechazada("El reloj no informa " + descartado.tipo());
			}
			if (descartado.cantidad() < 1) {
				throw new SolicitudRechazada("La cantidad de eventos descartados debe ser 1 o más");
			}
		}
	}

	/** @return por qué no se guarda la lectura, o null si se guarda */
	private static MotivoRechazo motivoRechazo(LecturaRecibida lectura, RelojAutenticado quien, Instant ahora) {
		if (!lectura.tipo().admite(lectura.valor())) {
			return MotivoRechazo.FUERA_DE_RANGO;
		}
		if (lectura.medicion().isAfter(ahora.plus(TOLERANCIA_FUTURO))) {
			return MotivoRechazo.HORA_FUTURA;
		}
		if (lectura.medicion().isBefore(quien.inicio())) {
			return MotivoRechazo.ANTERIOR_A_LA_ASIGNACION;
		}
		return null;
	}

	/** @return si la hora no es futura ni anterior a la entrega del reloj a este paciente */
	private static boolean horaPosible(Instant medicion, RelojAutenticado quien, Instant ahora) {
		return !medicion.isAfter(ahora.plus(TOLERANCIA_FUTURO)) && !medicion.isBefore(quien.inicio());
	}

	/** Un contador por tipo, como en la base: si el reloj repite un tipo, se suman. */
	private static List<EventoDescartado> sumarPorTipo(List<EventoDescartado> descartados) {
		Map<TipoEvento, Integer> suma = new EnumMap<>(TipoEvento.class);
		descartados.forEach(d -> suma.merge(d.tipo(), d.cantidad(), Integer::sum));
		return suma.entrySet().stream().map(e -> new EventoDescartado(e.getKey(), e.getValue())).toList();
	}

	/** @return segundos de adelanto (positivo) o atraso (negativo) del reloj, o null si es poco */
	private static Long desfase(Instant envio, Instant ahora) {
		Duration diferencia = Duration.between(ahora, envio);
		return diferencia.abs().compareTo(DESFASE_AVISADO) > 0 ? diferencia.toSeconds() : null;
	}

	/**
	 * @return la respuesta a un lote reenviado: lo que quedó guardado la primera vez, con la
	 *         versión de configuración que rige ahora (pudo cambiar desde el primer envío)
	 * @throws Conflicto si el lote es de otro reloj
	 */
	private ResultadoSincronizacion repetido(SincronizacionGuardada anterior, RelojAutenticado quien,
			LoteRecibido lote) {
		if (!anterior.idAsignacion().equals(quien.idAsignacion())) {
			throw new Conflicto("Ese eventId ya lo usó otro reloj");
		}
		String vigente = versiones.de(quien.idPaciente());
		return new ResultadoSincronizacion(anterior.id(), anterior.recepcion(), anterior.lecturas(), 0, List.of(),
				true, null, !vigente.equals(lote.estado().versionConfiguracion()), vigente);
	}

}
