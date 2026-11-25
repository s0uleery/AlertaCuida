package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.application.personal.CambiarTelefonoPersonal;
import cl.ucn.alertacuida.application.personal.ConsultarMiCuenta;
import cl.ucn.alertacuida.application.personal.modelo.MiCuenta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Mi cuenta": cualquier persona del equipo con sesión ve sus datos y enciende o apaga su WhatsApp. No exige
 * permisos: siempre actúa sobre la cuenta del token, nunca sobre una que venga en la petición.
 */
@RestController
@RequestMapping("/api/mi-cuenta")
public class MiCuentaController {

	private final ConsultarMiCuenta consultar;
	private final CambiarTelefonoPersonal cambiarTelefono;

	/**
	 * @param consultar       lectura de la cuenta propia
	 * @param cambiarTelefono el mismo cambio de teléfono y WhatsApp que usa Administración
	 */
	public MiCuentaController(ConsultarMiCuenta consultar, CambiarTelefonoPersonal cambiarTelefono) {
		this.consultar = consultar;
		this.cambiarTelefono = cambiarTelefono;
	}

	/** @return nombre, correo, teléfono y si recibe WhatsApp */
	@GetMapping
	public MiCuenta ver(@AuthenticationPrincipal Jwt jwt) {
		return consultar.consultar(TokenJwt.leer(jwt).id());
	}

	/**
	 * Cambia el teléfono y si recibe WhatsApp. El correo le sigue llegando siempre.
	 *
	 * @return 204. 400 si el número no sirve, se acepta WhatsApp sin teléfono o un coordinador queda sin teléfono
	 */
	@PutMapping("/whatsapp")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cambiarWhatsapp(@Valid @RequestBody SolicitudTelefono solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		UUID propia = TokenJwt.leer(jwt).id();
		cambiarTelefono.cambiar(propia, solicitud.telefono(), solicitud.aceptaWhatsapp(), propia,
				peticion.getRemoteAddr());
	}

}
