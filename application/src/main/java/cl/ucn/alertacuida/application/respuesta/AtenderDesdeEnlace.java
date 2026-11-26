package cl.ucn.alertacuida.application.respuesta;

import cl.ucn.alertacuida.application.alerta.AtenderAlerta;
import cl.ucn.alertacuida.application.alerta.RegistrarSeguimiento;
import cl.ucn.alertacuida.application.alerta.modelo.TipoSeguimiento;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.respuesta.error.EnlaceRespuestaInvalido;
import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.EstadoOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.EtapaOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.PasoOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoLlamadaOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoVisita;
import cl.ucn.alertacuida.application.respuesta.modelo.VistaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.PasosOficina;
import cl.ucn.alertacuida.application.respuesta.puerto.RespuestasContacto;
import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Los pasos de la oficina PRLAC desde el enlace del correo de un coordinador, iguales a los mensajes de WhatsApp
 * N1-02 a N1-11 (decisiones del 26-11-2026):
 * <ul>
 * <li>"Tomo la alerta" deja al coordinador a cargo (como "Asumir el caso" en el dashboard), pero no la confirma:
 * el reloj sigue avisando a los contactos.</li>
 * <li>La alerta se confirma, y el reloj deja de avisar, cuando la llamada dice que la persona está bien o cuando
 * se asigna personal para ir. Con "Sin disponibilidad" no se confirma: el reloj sigue.</li>
 * <li>"Ya llegó" no resuelve la alerta: falta el resultado de la visita. Cada cierre usa {@link AtenderAlerta}
 * (auditoría y aviso al reloj incluidos), con observaciones que dicen cómo se cerró.</li>
 * <li>Solo da los pasos quien tiene la alerta a cargo, y cada paso solo en su etapa
 * ({@link ArmarVistaOficina}); si no, 409 sin cambiar nada.</li>
 * </ul>
 */
public class AtenderDesdeEnlace {

	private static final String ENTIDAD = "ATENCION_ALERTA";
	private static final String MODIFICAR = "MODIFICAR";
	private static final String DESDE_EL_ENLACE = "Cerrada desde el enlace del correo: ";

	private final FirmaRespuesta firma;
	private final RespuestasContacto respuestas;
	private final PasosOficina pasos;
	private final ResponderAviso responder;
	private final AtenderAlerta atender;
	private final RegistrarSeguimiento seguimiento;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionRespuestaContacto.
	 *
	 * @param firma       reconoce el token del enlace
	 * @param respuestas  encuentra el aviso del enlace y deja el aviso ALERTA_CONFIRMADA al reloj
	 * @param pasos       lo que registra la oficina
	 * @param responder   arma la vista de la página después de cada paso
	 * @param atender     toma y cierra la alerta, igual que el dashboard
	 * @param seguimiento anota en el seguimiento lo que no es un paso (sigue en camino, derivación a SAMU)
	 */
	public AtenderDesdeEnlace(FirmaRespuesta firma, RespuestasContacto respuestas, PasosOficina pasos,
			ResponderAviso responder, AtenderAlerta atender, RegistrarSeguimiento seguimiento,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.firma = firma;
		this.respuestas = respuestas;
		this.pasos = pasos;
		this.responder = responder;
		this.atender = atender;
		this.seguimiento = seguimiento;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Da un paso de la oficina.
	 *
	 * @param token      el del enlace del correo
	 * @param paso       el botón que presionó
	 * @param comentario solo en {@link PasoOficina#COMENTARIO}
	 * @param ip         de quien lo pide, para la auditoría
	 * @return cómo quedó la página
	 * @throws EnlaceRespuestaInvalido si el token no sirve
	 * @throws Conflicto               si el enlace no es de un coordinador, la alerta la tiene otra persona o el
	 *                                 paso no corresponde a la etapa actual
	 * @throws SolicitudRechazada      si un comentario viene vacío
	 */
	public VistaRespuesta avanzar(String token, PasoOficina paso, String comentario, String ip) {
		Objects.requireNonNull(paso, "paso");
		UUID idIntento = firma.verificar(token).orElseThrow(EnlaceRespuestaInvalido::new);
		String texto = comentario == null || comentario.isBlank() ? null : comentario.strip();
		if (paso == PasoOficina.COMENTARIO && texto == null) {
			throw new SolicitudRechazada("Falta el comentario");
		}
		return transacciones.ejecutar(() -> {
			AvisoRespondible aviso = respuestas.buscar(idIntento).orElseThrow(EnlaceRespuestaInvalido::new);
			if (aviso.idCoordinador() == null) {
				throw new Conflicto("Este enlace no es de la oficina PRLAC");
			}
			EstadoOficina estado = pasos.buscar(aviso.idAlerta());
			EtapaOficina etapa = ArmarVistaOficina.etapa(aviso, estado);
			if (etapa == EtapaOficina.A_CARGO_DE_OTRO) {
				throw new Conflicto("La alerta está a cargo de " + estado.responsable());
			}
			new Paso(aviso, estado, ip, reloj.instant()).dar(paso, etapa, texto);
			return responder.consultarIntento(idIntento);
		});
	}

	/** Un paso de un coordinador sobre su alerta, con lo que hace falta para darlo. */
	private final class Paso {

		private final AvisoRespondible aviso;
		private final EstadoOficina estado;
		private final UUID alerta;
		private final UUID usuario;
		private final String ip;
		private final Instant ahora;

		/** @param ahora la hora del paso */
		Paso(AvisoRespondible aviso, EstadoOficina estado, String ip, Instant ahora) {
			this.aviso = aviso;
			this.estado = estado;
			this.alerta = aviso.idAlerta();
			this.usuario = aviso.idCoordinador();
			this.ip = ip;
			this.ahora = ahora;
		}

		/**
		 * @param etapa la actual
		 * @param texto el comentario, ya sin espacios en los bordes
		 * @throws Conflicto si el paso no corresponde a la etapa
		 */
		void dar(PasoOficina paso, EtapaOficina etapa, String texto) {
			switch (paso) {
				case TOMAR -> {
					exigir(etapa, EtapaOficina.SIN_TOMAR);
					atender.asumir(alerta, usuario, ip);
				}
				case PACIENTE_BIEN -> llamada(etapa, ResultadoLlamadaOficina.BIEN);
				case NECESITA_ATENCION -> llamada(etapa, ResultadoLlamadaOficina.NECESITA_ATENCION);
				case NO_CONTESTA -> llamada(etapa, ResultadoLlamadaOficina.NO_CONTESTA);
				case OCURRIO -> confirmarEvento(etapa, ClasificacionAlerta.POSITIVA, "el evento sí ocurrió.");
				case FALSA_ALARMA -> confirmarEvento(etapa, ClasificacionAlerta.FALSA_ALARMA, "fue una falsa alarma.");
				case PERSONAL_ASIGNADO -> {
					exigir(etapa, EtapaOficina.COORDINAR_VISITA);
					registrado(pasos.asignarPersonal(alerta, usuario, ahora));
					confirmar();
				}
				case SIN_DISPONIBILIDAD -> {
					exigir(etapa, EtapaOficina.COORDINAR_VISITA);
					registrado(pasos.sinDisponibilidad(alerta, usuario, ahora));
				}
				case LLEGO -> {
					exigir(etapa, EtapaOficina.ESPERANDO_LLEGADA);
					registrado(pasos.registrarLlegada(alerta, usuario, ahora));
				}
				case SIGUE_EN_CAMINO -> {
					exigir(etapa, EtapaOficina.ESPERANDO_LLEGADA);
					seguimiento.registrar(alerta, TipoSeguimiento.NOTA, null,
							"El personal asignado sigue en camino (indicado desde el enlace del correo).", usuario, ip);
				}
				case VISITA_BIEN -> visita(etapa, ResultadoVisita.BIEN);
				case APOYO_NO_URGENTE -> {
					visita(etapa, ResultadoVisita.APOYO_NO_URGENTE);
					cerrar(ClasificacionAlerta.POSITIVA, "la persona requiere apoyo no urgente. Coordinar el apoyo "
							+ "con el CESFAM y registrar las acciones en el seguimiento.");
				}
				case EMERGENCIA -> visita(etapa, ResultadoVisita.EMERGENCIA);
				case TRASLADADA -> {
					emergencia(etapa, "la persona fue trasladada.");
					seguimiento.registrar(alerta, TipoSeguimiento.DERIVACION, "SAMU", null, usuario, ip);
				}
				case ATENDIDA_EN_CASA -> emergencia(etapa, "la persona fue atendida en su domicilio.");
				case OTRO_RESULTADO -> emergencia(etapa, "otro resultado (ver los comentarios).");
				case COMENTARIO -> {
					exigir(etapa, EtapaOficina.CERRADA);
					if (!pasos.agregarComentario(alerta, usuario, texto)) {
						throw new Conflicto("Solo quien tiene la alerta a cargo puede comentar su cierre");
					}
					auditar();
				}
			}
		}

		/** Resultado de la llamada; si la persona está bien, la alerta queda confirmada. */
		private void llamada(EtapaOficina etapa, ResultadoLlamadaOficina resultado) {
			exigir(etapa, EtapaOficina.LLAMAR);
			registrado(pasos.registrarLlamada(alerta, usuario, resultado, ahora));
			if (resultado == ResultadoLlamadaOficina.BIEN) {
				confirmar();
			}
		}

		/** Cómo encontró el personal a la persona. */
		private void visita(EtapaOficina etapa, ResultadoVisita resultado) {
			exigir(etapa, EtapaOficina.RESULTADO_VISITA);
			registrado(pasos.registrarVisita(alerta, usuario, resultado, ahora));
		}

		/** Cierre de N1-03: la persona está bien y se dice si el evento ocurrió. */
		private void confirmarEvento(EtapaOficina etapa, ClasificacionAlerta clasificacion, String conclusion) {
			exigir(etapa, EtapaOficina.CONFIRMAR_EVENTO);
			String bien = estado.resultadoVisita() == ResultadoVisita.BIEN ? "el personal encontró bien a la persona; "
					: "la persona está bien; ";
			cerrar(clasificacion, bien + conclusion);
		}

		/** Cierre de N1-11: una emergencia ya tiene desenlace. */
		private void emergencia(EtapaOficina etapa, String desenlace) {
			exigir(etapa, EtapaOficina.EMERGENCIA);
			cerrar(ClasificacionAlerta.POSITIVA, "emergencia; " + desenlace);
		}

		/** Cierra la alerta igual que el dashboard, con observaciones que dicen cómo. */
		private void cerrar(ClasificacionAlerta clasificacion, String como) {
			atender.resolver(alerta, clasificacion, DESDE_EL_ENLACE + como, usuario, ip);
		}

		/** La alerta queda confirmada: si es del reloj, se le avisa para que deje de escalar. */
		private void confirmar() {
			if (aviso.delReloj()) {
				respuestas.dejarAvisoConfirmadaPendiente(alerta);
			}
		}

		/** @throws Conflicto si la base no lo guardó: otra pestaña dio otro paso a la vez */
		private void registrado(boolean guardado) {
			if (!guardado) {
				throw new Conflicto("La alerta avanzó mientras tanto. Vuelva a abrir el enlace");
			}
			auditar();
		}

		/** Deja el paso en la auditoría, como cualquier cambio en la atención. */
		private void auditar() {
			auditoria.registrar(usuario, MODIFICAR, ENTIDAD, alerta.toString(), ip, ahora);
		}

	}

	/** @throws Conflicto si el paso no es de la etapa actual */
	private static void exigir(EtapaOficina actual, EtapaOficina esperada) {
		if (actual != esperada) {
			throw new Conflicto(actual == EtapaOficina.CERRADA ? "La alerta ya está cerrada"
					: "Ese paso ya no corresponde: la alerta avanzó. Vuelva a abrir el enlace");
		}
	}

}
