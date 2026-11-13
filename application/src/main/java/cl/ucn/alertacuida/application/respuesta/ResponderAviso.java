package cl.ucn.alertacuida.application.respuesta;

import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.respuesta.error.EnlaceRespuestaInvalido;
import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.application.respuesta.modelo.VistaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.RespuestasContacto;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import java.time.Clock;
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
		Objects.requireNonNull(respuesta, "respuesta");
		UUID idIntento = idDe(token);
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

	/** @return lo que ve el contacto */
	private static VistaRespuesta vista(AvisoRespondible aviso) {
		return new VistaRespuesta(aviso.numero(), aviso.alias(), aviso.tipo(), aviso.severidad(), aviso.fechaHora(),
				aviso.respuesta(), aviso.fechaRespuesta(), cerrada(aviso.estado()));
	}

}
