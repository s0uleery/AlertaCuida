package cl.ucn.alertacuida.application.respuesta;

import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.ContactoAvisado;
import cl.ucn.alertacuida.application.respuesta.modelo.EstadoOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.EtapaOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoLlamadaOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoVisita;
import cl.ucn.alertacuida.application.respuesta.modelo.VistaOficina;
import cl.ucn.alertacuida.application.respuesta.puerto.PasosOficina;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Calcula en qué va la oficina PRLAC con una alerta y arma lo que ve el coordinador en la página del enlace del
 * correo (decisiones del 26-11-2026). La etapa no se guarda: sale de lo registrado, en este orden (gana la
 * primera que calza):
 * <ol>
 * <li>Cerrada o cancelada → {@link EtapaOficina#CERRADA}.</li>
 * <li>Nadie la tomó → {@link EtapaOficina#SIN_TOMAR}; la tiene otra cuenta →
 * {@link EtapaOficina#A_CARGO_DE_OTRO}.</li>
 * <li>Visita con emergencia → {@link EtapaOficina#EMERGENCIA}; visita con la persona bien →
 * {@link EtapaOficina#CONFIRMAR_EVENTO}; el personal llegó → {@link EtapaOficina#RESULTADO_VISITA}.</li>
 * <li>Personal asignado → {@link EtapaOficina#ESPERANDO_LLEGADA}; sin disponibilidad →
 * {@link EtapaOficina#SIN_DISPONIBILIDAD}.</li>
 * <li>Llamada con la persona bien → {@link EtapaOficina#CONFIRMAR_EVENTO}; necesita atención o no contesta →
 * {@link EtapaOficina#COORDINAR_VISITA}; sin llamada → {@link EtapaOficina#LLAMAR}.</li>
 * </ol>
 */
public class ArmarVistaOficina {

	private final PasosOficina pasos;
	private final Duration plazoRecordatorio;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionRespuestaContacto.
	 *
	 * @param pasos             lo que registró la oficina
	 * @param plazoRecordatorio cuánto puede tardar el personal en llegar antes del recordatorio (N1-08)
	 */
	public ArmarVistaOficina(PasosOficina pasos, Duration plazoRecordatorio, Clock reloj) {
		this.pasos = pasos;
		this.plazoRecordatorio = plazoRecordatorio;
		this.reloj = reloj;
	}

	/**
	 * @param aviso el del enlace
	 * @return lo que ve el coordinador; null si el aviso es de un contacto del paciente
	 */
	public VistaOficina de(AvisoRespondible aviso) {
		if (aviso.idCoordinador() == null) {
			return null;
		}
		EstadoOficina estado = pasos.buscar(aviso.idAlerta());
		EtapaOficina etapa = etapa(aviso, estado);
		boolean recordatorio = etapa == EtapaOficina.ESPERANDO_LLEGADA
				&& !estado.personalAsignado().plus(plazoRecordatorio).isAfter(reloj.instant());
		ContactoAvisado ultimo = etapa == EtapaOficina.SIN_DISPONIBILIDAD
				? pasos.ultimoContactoAvisado(aviso.idAlerta()).orElse(null)
				: null;
		return new VistaOficina(aviso.idAlerta(), etapa, estado.responsable(),
				aviso.idCoordinador().equals(estado.idResponsable()), estado.resultadoLlamada(),
				estado.personalAsignado(), estado.sinDisponibilidad(), estado.llegada(), estado.resultadoVisita(),
				recordatorio, estado.cierre(), estado.clasificacion(), ultimo == null ? null : ultimo.nombre(),
				ultimo == null ? null : ultimo.envio());
	}

	/**
	 * @param aviso  el del enlace, de un coordinador
	 * @param estado lo que registró la oficina de su alerta
	 * @return la etapa, según el orden de la clase
	 */
	static EtapaOficina etapa(AvisoRespondible aviso, EstadoOficina estado) {
		if (aviso.estado() == EstadoContacto.RESUELTA || aviso.estado() == EstadoContacto.CANCELADA) {
			return EtapaOficina.CERRADA;
		}
		if (estado.idResponsable() == null) {
			return EtapaOficina.SIN_TOMAR;
		}
		if (!Objects.equals(estado.idResponsable(), aviso.idCoordinador())) {
			return EtapaOficina.A_CARGO_DE_OTRO;
		}
		if (estado.resultadoVisita() != null) {
			return estado.resultadoVisita() == ResultadoVisita.EMERGENCIA ? EtapaOficina.EMERGENCIA
					: EtapaOficina.CONFIRMAR_EVENTO;
		}
		if (estado.llegada() != null) {
			return EtapaOficina.RESULTADO_VISITA;
		}
		if (estado.personalAsignado() != null) {
			return EtapaOficina.ESPERANDO_LLEGADA;
		}
		if (estado.sinDisponibilidad() != null) {
			return EtapaOficina.SIN_DISPONIBILIDAD;
		}
		if (estado.resultadoLlamada() == null) {
			return EtapaOficina.LLAMAR;
		}
		return estado.resultadoLlamada() == ResultadoLlamadaOficina.BIEN ? EtapaOficina.CONFIRMAR_EVENTO
				: EtapaOficina.COORDINAR_VISITA;
	}

}
