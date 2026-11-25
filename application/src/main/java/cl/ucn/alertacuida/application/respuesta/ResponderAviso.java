package cl.ucn.alertacuida.application.respuesta;

import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.respuesta.error.EnlaceRespuestaInvalido;
import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.application.respuesta.modelo.VistaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.RespuestasContacto;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Respuesta de un contacto del paciente desde los enlaces de su correo ("Confirmo que voy" y "No puedo
 * asistir"), que cuenta igual que los botones del WhatsApp. Decisiones del 12-11-2026:
 * <ul>
 * <li>Abrir el enlace solo muestra la alerta ({@link #consultar}); la respuesta se registra al presionar un
 * botón de la página ({@link #responder}). Así, los revisores de enlaces del correo no responden por la
 * persona.</li>
 * <li>Vale solo la primera respuesta.</li>
 * <li>Si la alerta fue resuelta o cancelada, no se registra. Si otro contacto ya confirmó o el reloj terminó
 * la lista sin respuesta, sí: un "voy" tardío le sirve al equipo.</li>
 * <li>"Voy" confirma la alerta y deja el aviso ALERTA_CONFIRMADA para que el reloj deje de escalar.</li>
 * </ul>
 * Decisiones del 24-11-2026:
 * <ul>
 * <li>Mientras la alerta está abierta, la página ofrece primero "Llamar al paciente", al número de la SIM del
 * reloj ({@link #llamar}); el clic queda registrado (solo el primero), aunque no se sabe si contestaron.</li>
 * <li>Quien respondió "Voy en camino" avisa al llegar donde el paciente ({@link #llegar}): se registra y, si la
 * alerta sigue abierta, la resuelve el sistema y avisa al reloj. El equipo completa después la clasificación y
 * las observaciones.</li>
 * </ul>
 */
public class ResponderAviso {

	private final FirmaRespuesta firma;
	private final RespuestasContacto respuestas;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionRespuestaContacto.
	 *
	 * @param firma      reconoce el token del enlace
	 * @param respuestas los avisos y sus respuestas
	 */
	public ResponderAviso(FirmaRespuesta firma, RespuestasContacto respuestas, Transacciones transacciones,
			Clock reloj) {
		this.firma = firma;
		this.respuestas = respuestas;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param token el del enlace del correo
	 * @return la alerta y lo que ya respondió, sin registrar nada
	 * @throws EnlaceRespuestaInvalido si el token no sirve
	 */
	public VistaRespuesta consultar(String token) {
		return vista(buscar(idDe(token)));
	}

	/**
	 * Registra la respuesta, si corresponde (ver la clase).
	 *
	 * @param token     el del enlace del correo
	 * @param respuesta el botón que presionó
	 * @return cómo quedó: con la primera respuesta si ya había una, o cerrada si la alerta terminó
	 * @throws EnlaceRespuestaInvalido si el token no sirve
	 */
	public VistaRespuesta responder(String token, RespuestaContacto respuesta) {
		return responderIntento(idDe(token), respuesta);
	}

	/**
	 * Registra la respuesta a ese aviso, con las mismas reglas que el enlace del correo. Lo usa también el
	 * webhook de WhatsApp, que encuentra el aviso por el mensaje que tenía el botón (24-11-2026).
	 *
	 * @param idIntento el aviso
	 * @param respuesta el botón que presionó
	 * @return cómo quedó
	 * @throws EnlaceRespuestaInvalido si ese aviso no confirma alertas
	 */
	public VistaRespuesta responderIntento(UUID idIntento, RespuestaContacto respuesta) {
		Objects.requireNonNull(respuesta, "respuesta");
		return transacciones.ejecutar(() -> {
			AvisoRespondible aviso = buscar(idIntento);
			if (aviso.respuesta() != null || cerrada(aviso.estado())) {
				return vista(aviso);
			}
			boolean registrada = respuestas.registrar(idIntento, respuesta, reloj.instant());
			if (registrada && respuesta == RespuestaContacto.VOY_EN_CAMINO) {
				respuestas.dejarAvisoConfirmadaPendiente(aviso.idAlerta());
			}
			return vista(buscar(idIntento));
		});
	}

	/**
	 * Registra que el contacto presionó "Llamar al paciente" (solo la primera vez, y solo con la alerta
	 * abierta). La llamada la hace su teléfono.
	 *
	 * @param token el del enlace del correo
	 * @throws EnlaceRespuestaInvalido si el token no sirve
	 */
	public void llamar(String token) {
		UUID idIntento = idDe(token);
		transacciones.ejecutar(() -> {
			if (!cerrada(buscar(idIntento).estado())) {
				respuestas.registrarLlamada(idIntento, reloj.instant());
			}
			return null;
		});
	}

	/**
	 * Registra que el contacto llegó donde el paciente y, si la alerta sigue abierta, la resuelve. Repetirlo no
	 * cambia nada.
	 *
	 * @param token el del enlace del correo
	 * @return cómo quedó, con la hora de llegada
	 * @throws EnlaceRespuestaInvalido si el token no sirve
	 * @throws Conflicto               si este contacto no respondió "Voy en camino"
	 */
	public VistaRespuesta llegar(String token) {
		UUID idIntento = idDe(token);
		return transacciones.ejecutar(() -> {
			AvisoRespondible aviso = buscar(idIntento);
			if (aviso.respuesta() != RespuestaContacto.VOY_EN_CAMINO) {
				throw new Conflicto("Primero confirma que vas en camino");
			}
			Instant ahora = reloj.instant();
			if (aviso.llegada() == null && respuestas.registrarLlegada(idIntento, ahora) && !cerrada(aviso.estado())
					&& respuestas.resolverPorLlegada(aviso.idAlerta(), ahora) && aviso.delReloj()) {
				respuestas.dejarAvisoResueltaPendiente(aviso.idAlerta());
			}
			return vista(buscar(idIntento));
		});
	}

	/** @return el aviso del token, si la firma es de este servidor */
	private UUID idDe(String token) {
		return firma.verificar(token).orElseThrow(EnlaceRespuestaInvalido::new);
	}

	/** @return el aviso, si es un correo a un contacto del paciente */
	private AvisoRespondible buscar(UUID idIntento) {
		return respuestas.buscar(idIntento).orElseThrow(EnlaceRespuestaInvalido::new);
	}

	/** @return si la alerta terminó para el equipo, así que ya no hace falta ir */
	private static boolean cerrada(EstadoContacto estado) {
		return estado == EstadoContacto.RESUELTA || estado == EstadoContacto.CANCELADA;
	}

	/** @return lo que ve el contacto; el número para llamar, solo con la alerta abierta */
	private static VistaRespuesta vista(AvisoRespondible aviso) {
		boolean cerrada = cerrada(aviso.estado());
		return new VistaRespuesta(aviso.numero(), aviso.alias(), aviso.tipo(), aviso.severidad(), aviso.fechaHora(),
				aviso.respuesta(), aviso.fechaRespuesta(), cerrada, cerrada ? null : aviso.telefonoReloj(),
				aviso.llegada());
	}

}
