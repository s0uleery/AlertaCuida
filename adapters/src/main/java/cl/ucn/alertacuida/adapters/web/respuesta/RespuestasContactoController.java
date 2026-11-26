package cl.ucn.alertacuida.adapters.web.respuesta;

import cl.ucn.alertacuida.application.respuesta.AtenderDesdeEnlace;
import cl.ucn.alertacuida.application.respuesta.ResponderAviso;
import cl.ucn.alertacuida.application.respuesta.modelo.PasoOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoLlamadaContacto;
import cl.ucn.alertacuida.application.respuesta.modelo.VistaRespuesta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Respuesta de un contacto del paciente desde los enlaces de su correo (página /responder del dashboard). Es
 * pública (ver ConfiguracionSeguridad): el contacto no tiene cuenta, así que lo autoriza el token firmado del
 * enlace. El token va en el cuerpo y no en la dirección, para que no quede en los registros del servidor.
 * Todas son POST: así los revisores de enlaces del correo, que solo abren direcciones, no las disparan.
 */
@RestController
@RequestMapping("/api/respuestas-contacto")
public class RespuestasContactoController {

	private final ResponderAviso responder;
	private final AtenderDesdeEnlace oficina;

	/**
	 * @param responder caso de uso de la respuesta del contacto
	 * @param oficina   caso de uso de los pasos de la oficina, desde el enlace de un coordinador
	 */
	public RespuestasContactoController(ResponderAviso responder, AtenderDesdeEnlace oficina) {
		this.responder = responder;
		this.oficina = oficina;
	}

	/**
	 * Cuerpo de un paso de la oficina.
	 *
	 * @param token      lo que viene después de "#" en el enlace
	 * @param paso       el botón que presionó
	 * @param comentario solo en COMENTARIO
	 */
	public record SolicitudPaso(@NotBlank String token, @NotNull PasoOficina paso,
			@Size(max = 2000) String comentario) {

		/** Sin el token ni el comentario: que no aparezcan en los logs. */
		@Override
		public String toString() {
			return "SolicitudPaso[" + paso + "]";
		}

	}

	/**
	 * Cuerpo del resultado de la llamada del contacto.
	 *
	 * @param token     lo que viene después de "#" en el enlace
	 * @param resultado si le contestaron
	 */
	public record SolicitudResultadoLlamada(@NotBlank String token, @NotNull ResultadoLlamadaContacto resultado) {

		/** Sin el token: que no aparezca en los logs. */
		@Override
		public String toString() {
			return "SolicitudResultadoLlamada[" + resultado + "]";
		}

	}

	/**
	 * Cuerpo de la consulta.
	 *
	 * @param token lo que viene después de "#" en el enlace
	 */
	public record SolicitudConsulta(@NotBlank String token) {

		/** Sin el token: que no aparezca en los logs. */
		@Override
		public String toString() {
			return "SolicitudConsulta[]";
		}

	}

	/**
	 * Cuerpo de la respuesta.
	 *
	 * @param token     lo que viene después de "#" en el enlace
	 * @param respuesta el botón que presionó
	 */
	public record SolicitudRespuesta(@NotBlank String token, @NotNull RespuestaContacto respuesta) {

		/** Sin el token: que no aparezca en los logs. */
		@Override
		public String toString() {
			return "SolicitudRespuesta[" + respuesta + "]";
		}

	}

	/**
	 * Lo que muestra la página al abrir el enlace. No registra nada.
	 *
	 * @return 200 con la alerta y lo que ya respondió. 400 si el enlace no sirve
	 */
	@PostMapping("/consulta")
	public VistaRespuesta consultar(@Valid @RequestBody SolicitudConsulta solicitud) {
		return responder.consultar(solicitud.token());
	}

	/**
	 * Registra la respuesta, si aún no hay una y la alerta no fue resuelta ni cancelada.
	 *
	 * @return 200 con cómo quedó (la primera respuesta, si ya había una). 400 si el enlace no sirve
	 */
	@PostMapping
	public VistaRespuesta responder(@Valid @RequestBody SolicitudRespuesta solicitud) {
		return responder.responder(solicitud.token(), solicitud.respuesta());
	}


	/**
	 * Registra que el contacto presionó "Llamar al paciente" (solo la primera vez, con la alerta abierta). La
	 * página abre después el marcador del teléfono.
	 *
	 * @return 204. 400 si el enlace no sirve
	 */
	@PostMapping("/llamada")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void llamar(@Valid @RequestBody SolicitudConsulta solicitud) {
		responder.llamar(solicitud.token());
	}

	/**
	 * Registra que el contacto llegó donde el paciente y, si la alerta sigue abierta, la resuelve.
	 *
	 * @return 200 con cómo quedó. 400 si el enlace no sirve; 409 si no respondió "Voy en camino"
	 */
	@PostMapping("/llegada")
	public VistaRespuesta llegar(@Valid @RequestBody SolicitudConsulta solicitud) {
		return responder.llegar(solicitud.token());
	}

	/**
	 * Registra si al contacto le contestaron después de llamar (solo la primera vez, con la alerta abierta).
	 *
	 * @return 200 con cómo quedó. 400 si el enlace no sirve; 409 si el enlace es de un coordinador
	 */
	@PostMapping("/resultado-llamada")
	public VistaRespuesta resultadoLlamada(@Valid @RequestBody SolicitudResultadoLlamada solicitud) {
		return responder.registrarResultadoLlamada(solicitud.token(), solicitud.resultado());
	}

	/**
	 * Da un paso de la oficina PRLAC (los mismos de los mensajes de WhatsApp N1).
	 *
	 * @return 200 con cómo quedó. 400 si el enlace no sirve o falta el comentario; 409 si el enlace no es de un
	 *         coordinador, otra persona tiene la alerta o el paso no corresponde a la etapa actual
	 */
	@PostMapping("/paso")
	public VistaRespuesta paso(@Valid @RequestBody SolicitudPaso solicitud, HttpServletRequest peticion) {
		return oficina.avanzar(solicitud.token(), solicitud.paso(), solicitud.comentario(),
				peticion.getRemoteAddr());
	}

}
