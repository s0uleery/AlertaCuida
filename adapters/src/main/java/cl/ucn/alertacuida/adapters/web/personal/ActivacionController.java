package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.application.personal.ActivarCuenta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Activación de una cuenta con su enlace. Es pública (ver ConfiguracionSeguridad):
 * la persona todavía no tiene contraseña, así que la autoriza el token del enlace.
 */
@RestController
public class ActivacionController {

	private final ActivarCuenta activarCuenta;

	/** @param activarCuenta caso de uso de la activación */
	public ActivacionController(ActivarCuenta activarCuenta) {
		this.activarCuenta = activarCuenta;
	}

	/**
	 * Guarda la contraseña elegida y activa la cuenta. Después, la persona inicia sesión normalmente.
	 *
	 * @param solicitud token del enlace y contraseña
	 * @return 204 sin cuerpo. 400 si el enlace no sirve o la contraseña no cumple la política
	 */
	@PostMapping("/api/auth/activar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void activar(@Valid @RequestBody SolicitudActivacion solicitud, HttpServletRequest peticion) {
		activarCuenta.activar(solicitud.token(), solicitud.contrasena(), peticion.getRemoteAddr());
	}

}
