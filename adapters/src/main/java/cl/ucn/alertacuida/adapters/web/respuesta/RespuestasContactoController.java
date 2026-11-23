package cl.ucn.alertacuida.adapters.web.respuesta;

import cl.ucn.alertacuida.application.respuesta.ResponderAviso;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.application.respuesta.modelo.VistaRespuesta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Respuesta de un contacto del paciente desde los enlaces de su correo (página /responder del dashboard). Es
 * pública (ver ConfiguracionSeguridad): el contacto no tiene cuenta, así que lo autoriza el token firmado del
 * enlace. El token va en el cuerpo y no en la dirección, para que no quede en los registros del servidor.
 * Las dos son POST: así los revisores de enlaces del correo, que solo abren direcciones, no las disparan.
 */
@RestController
@RequestMapping("/api/respuestas-contacto")
public class RespuestasContactoController {

	private final ResponderAviso responder;

	/** @param responder caso de uso de la respuesta */
	public RespuestasContactoController(ResponderAviso responder) {
		this.responder = responder;
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

}
