package cl.ucn.alertacuida.application.escalamiento;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.AlertasDelReloj;
import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.puerto.ListaDelPaciente;
import cl.ucn.alertacuida.application.escalamiento.modelo.IntentoExistente;
import cl.ucn.alertacuida.application.escalamiento.modelo.MotivoRechazo;
import cl.ucn.alertacuida.application.escalamiento.modelo.RespuestaAviso;
import cl.ucn.alertacuida.application.escalamiento.modelo.SolicitudAviso;
import cl.ucn.alertacuida.application.escalamiento.puerto.IntentosAviso;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * El reloj pide avisar a un contacto de su alerta. El reloj decide a quién y cuándo (prioridad, horario,
 * gravedad); el servidor revisa que pueda hacerlo y lo deja en la bandeja de salida, de donde sale por
 * WhatsApp o correo. Si no puede, responde RECHAZADO con el motivo y el reloj pasa al siguiente: es parte
 * normal del flujo. No envía dos veces, ni con el mismo eventId ni con el mismo contacto, canal y ronda.
 */
public class SolicitarAviso {

	private final AlertasDelReloj alertas;
	private final ListaDelPaciente contactos;
	private final IntentosAviso intentos;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionEscalamiento. */
	public SolicitarAviso(AlertasDelReloj alertas, ListaDelPaciente contactos, IntentosAviso intentos,
			Transacciones transacciones, Clock reloj) {
		this.alertas = alertas;
		this.contactos = contactos;
		this.intentos = intentos;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param quien         el reloj que lo pide, según su token
	 * @param eventIdAlerta el de la alerta
	 * @param solicitud     contacto, canal y ronda, con el eventId de esta solicitud
	 * @return ACEPTADO con el intento, o RECHAZADO con el motivo; siempre con el estado de la alerta
	 * @throws SolicitudRechazada si falta un dato o la ronda no es 1 ni 2
	 * @throws NoEncontrado       si no hay una alerta con ese eventId de este reloj
	 * @throws Conflicto          si el eventId de la solicitud ya se usó para un aviso de otra alerta
	 */
	public RespuestaAviso solicitar(RelojAutenticado quien, UUID eventIdAlerta, SolicitudAviso solicitud) {
		validar(solicitud);
		AlertaGuardada alerta = alertas.buscar(eventIdAlerta)
				.filter(a -> a.idAsignacion().equals(quien.idAsignacion()))
				.orElseThrow(() -> new NoEncontrado("No hay una alerta con ese eventId para este reloj"));
		Instant ahora = reloj.instant();
		return transacciones.ejecutar(() -> atender(quien, alerta.idAlerta(), solicitud, ahora));
	}

	/** Revisa la solicitud y, si se puede, deja el aviso en la bandeja. */
	private RespuestaAviso atender(RelojAutenticado quien, UUID idAlerta, SolicitudAviso solicitud, Instant ahora) {
		Optional<IntentoExistente> repetido = intentos.porEventId(solicitud.eventId());
		if (repetido.isPresent()) {
			return reintento(repetido.get(), idAlerta);
		}
		EstadoAlerta estado = alertas.estado(idAlerta);
		if (estado.detenerEscalamiento()) {
			return RespuestaAviso.rechazado(MotivoRechazo.ALERTA_TERMINADA, estado);
		}
		Optional<MotivoRechazo> motivo = motivoRechazo(quien.idPaciente(), solicitud);
		if (motivo.isPresent()) {
			return RespuestaAviso.rechazado(motivo.get(), estado);
		}
		Optional<UUID> existente = mismoDestino(idAlerta, solicitud);
		if (existente.isPresent()) {
			return RespuestaAviso.aceptado(existente.get(), estado);
		}
		Optional<UUID> nuevo = intentos.encolar(solicitud.eventId(), idAlerta, solicitud.idContacto(),
				solicitud.canal(), solicitud.ronda(), ahora);
		if (nuevo.isEmpty()) {
			// Otra petición igual llegó a la vez: vale la que quedó guardada
			return intentos.porEventId(solicitud.eventId()).map(intento -> reintento(intento, idAlerta))
					.orElseGet(() -> RespuestaAviso.aceptado(mismoDestino(idAlerta, solicitud).orElseThrow(),
							alertas.estado(idAlerta)));
		}
		return RespuestaAviso.aceptado(nuevo.get(), alertas.estado(idAlerta));
	}

	/**
	 * El reloj repitió una solicitud: se responde con el mismo aviso.
	 *
	 * @throws Conflicto si ese eventId era de un aviso de otra alerta
	 */
	private RespuestaAviso reintento(IntentoExistente intento, UUID idAlerta) {
		if (!intento.idAlerta().equals(idAlerta)) {
			throw new Conflicto("Ese eventId ya se usó para un aviso de otra alerta");
		}
		return RespuestaAviso.aceptado(intento.idIntento(), alertas.estado(idAlerta));
	}

	/** @return el aviso ya registrado a ese contacto, canal y ronda */
	private Optional<UUID> mismoDestino(UUID idAlerta, SolicitudAviso solicitud) {
		return intentos.mismoDestino(idAlerta, solicitud.idContacto(), solicitud.canal(), solicitud.ronda());
	}

	/**
	 * Usa la misma regla que la configuración del reloj ({@link ContactoDelPaciente#canales()}), para que
	 * el servidor no rechace lo que la configuración ofrece.
	 *
	 * @return por qué no se puede avisar a ese contacto por ese canal, o vacío si se puede
	 */
	private Optional<MotivoRechazo> motivoRechazo(UUID idPaciente, SolicitudAviso solicitud) {
		Optional<ContactoDelPaciente> contacto = contactos.delPaciente(idPaciente).stream()
				.filter(c -> c.id().equals(solicitud.idContacto()))
				.findFirst();
		if (contacto.isEmpty()) {
			return Optional.of(MotivoRechazo.CONTACTO_DESCONOCIDO);
		}
		if (!contacto.get().activo()) {
			return Optional.of(MotivoRechazo.CONTACTO_INACTIVO);
		}
		if (contacto.get().canales().contains(solicitud.canal())) {
			return Optional.empty();
		}
		boolean tieneWhatsapp = contacto.get().persona().whatsapp() != null;
		return Optional.of(solicitud.canal() == Canal.WHATSAPP && tieneWhatsapp ? MotivoRechazo.SIN_OPT_IN
				: MotivoRechazo.CANAL_NO_DISPONIBLE);
	}

	/** @throws SolicitudRechazada si falta un dato o la ronda no es 1 ni 2 */
	private static void validar(SolicitudAviso solicitud) {
		if (solicitud.eventId() == null || solicitud.idContacto() == null || solicitud.canal() == null
				|| solicitud.ronda() == null) {
			throw new SolicitudRechazada("Faltan eventId, idContacto, canal o ronda");
		}
		if (solicitud.ronda() != 1 && solicitud.ronda() != 2) {
			throw new SolicitudRechazada("La ronda debe ser 1 o 2");
		}
	}

}
