package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.application.personal.RestablecerContrasena;
import cl.ucn.alertacuida.application.personal.SolicitarRecuperacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recuperación de la contraseña desde el inicio de sesión. Es pública (ver ConfiguracionSeguridad): quien la
 * usa no puede entrar. Al elegir la contraseña nueva, lo autoriza el token del enlace que le llegó por correo.
 */
@RestController
public class RecuperacionController {

	private final SolicitarRecuperacion solicitar;
	private final RestablecerContrasena restablecer;

	/**
	 * @param solicitar   envío del enlace por correo
	 * @param restablecer cambio de la contraseña con el enlace
	 */
	public RecuperacionController(SolicitarRecuperacion solicitar, RestablecerContrasena restablecer) {
		this.solicitar = solicitar;
		this.restablecer = restablecer;
	}

	/**
	 * Envía el enlace si la cuenta está activa. La respuesta es siempre la misma, exista o no la cuenta.
	 *
	 * @param solicitud el RUN
	 * @return 202 sin cuerpo; 400 solo si el RUN está mal escrito
	 */
	@PostMapping("/api/auth/recuperar")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void recuperar(@Valid @RequestBody SolicitudRecuperacion solicitud) {
		solicitar.solicitar(solicitud.run());
	}

	/**
	 * Guarda la contraseña nueva. Después, la persona inicia sesión con ella.
	 *
	 * @param solicitud token del enlace y contraseña nueva
	 * @return 204 sin cuerpo. 400 si el enlace no sirve o la contraseña no cumple la política
	 */
	@PostMapping("/api/auth/restablecer")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restablecer(@Valid @RequestBody SolicitudActivacion solicitud, HttpServletRequest peticion) {
		restablecer.restablecer(solicitud.token(), solicitud.contrasena(), peticion.getRemoteAddr());
	}

}
