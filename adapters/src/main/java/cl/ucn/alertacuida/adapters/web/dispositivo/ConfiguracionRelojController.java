package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.adapters.seguridad.dispositivo.PrincipalReloj;
import cl.ucn.alertacuida.application.configuracion.ConfiguracionDelReloj;
import cl.ucn.alertacuida.application.configuracion.modelo.ConfiguracionReloj;
import cl.ucn.alertacuida.application.reloj.RegistrarTokenFcm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Configuración que descarga el reloj y registro de su token de Firebase. Solo los llama un reloj
 * con su token (lo exige la cadena de seguridad de /api/reloj).
 */
@RestController
@RequestMapping("/api/reloj/v1")
public class ConfiguracionRelojController {

	private final ConfiguracionDelReloj configuraciones;
	private final RegistrarTokenFcm tokensFcm;

	/**
	 * @param configuraciones arma la configuración vigente del paciente
	 * @param tokensFcm       guarda el token de Firebase del reloj
	 */
	public ConfiguracionRelojController(ConfiguracionDelReloj configuraciones, RegistrarTokenFcm tokensFcm) {
		this.configuraciones = configuraciones;
		this.tokensFcm = tokensFcm;
	}

	/**
	 * La versión viaja como ETag. Si el reloj envía {@code If-None-Match} con la versión vigente,
	 * Spring responde 304 sin cuerpo: la configuración no se reenvía si no cambió.
	 *
	 * @return 200 con la configuración y su ETag, o 304 (null) si el reloj ya la tiene
	 */
	@GetMapping("/configuracion")
	public RespuestaConfiguracion configuracion(@AuthenticationPrincipal PrincipalReloj principal,
			WebRequest peticion) {
		ConfiguracionReloj configuracion = configuraciones.armar(principal.reloj().idPaciente());
		if (peticion.checkNotModified("\"" + configuracion.version() + "\"")) {
			return null;
		}
		return RespuestaConfiguracion.de(configuracion);
	}

	/** Guarda o renueva el token de Firebase. 204 sin cuerpo; 400 si viene vacío o es muy largo. */
	@PutMapping("/token-fcm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void tokenFcm(@Valid @RequestBody SolicitudTokenFcm solicitud,
			@AuthenticationPrincipal PrincipalReloj principal) {
		tokensFcm.registrar(principal.reloj(), solicitud.token());
	}

}
