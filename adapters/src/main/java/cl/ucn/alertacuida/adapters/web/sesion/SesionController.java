package cl.ucn.alertacuida.adapters.web.sesion;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.application.sesion.IniciarSesion;
import cl.ucn.alertacuida.application.sesion.InicioSesionFallido;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inicio de sesión del personal (1.3.3). Solo traduce HTTP: las reglas están en IniciarSesion. */
@RestController
@RequestMapping("/api/auth")
public class SesionController {

	private final IniciarSesion iniciarSesion;

	public SesionController(IniciarSesion iniciarSesion) {
		this.iniciarSesion = iniciarSesion;
	}

	@PostMapping("/login")
	public RespuestaLogin login(@Valid @RequestBody SolicitudLogin solicitud, HttpServletRequest peticion) {
		return RespuestaLogin.de(iniciarSesion.iniciar(solicitud.run(), solicitud.contrasena(),
				peticion.getRemoteAddr()));
	}

	/** Quién tiene la sesión: el front lo usa al recargar la página. */
	@GetMapping("/yo")
	public UsuarioRespuesta yo(@AuthenticationPrincipal Jwt jwt) {
		return UsuarioRespuesta.de(TokenJwt.leer(jwt));
	}

	@ExceptionHandler(InicioSesionFallido.class)
	ProblemDetail inicioFallido(InicioSesionFallido fallo) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, fallo.getMessage());
	}

}
