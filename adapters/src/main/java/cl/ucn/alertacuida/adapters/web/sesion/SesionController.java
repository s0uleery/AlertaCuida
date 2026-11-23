package cl.ucn.alertacuida.adapters.web.sesion;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.application.sesion.CerrarSesion;
import cl.ucn.alertacuida.application.sesion.IniciarSesion;
import cl.ucn.alertacuida.application.sesion.RenovarSesion;
import cl.ucn.alertacuida.application.sesion.error.InicioSesionFallido;
import cl.ucn.alertacuida.application.sesion.error.SesionNoRenovable;
import cl.ucn.alertacuida.application.sesion.modelo.SesionIniciada;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inicio, renovación y cierre de sesión del personal. Solo traduce HTTP: las reglas están en los casos de uso. */
@RestController
@RequestMapping("/api/auth")
public class SesionController {

	private final IniciarSesion iniciarSesion;
	private final RenovarSesion renovarSesion;
	private final CerrarSesion cerrarSesion;
	private final CookieRenovacion cookie;
	private final Duration inactividad;

	/**
	 * @param iniciarSesion caso de uso del inicio de sesión
	 * @param renovarSesion caso de uso de la renovación
	 * @param cerrarSesion  caso de uso del cierre
	 * @param cookie        arma la cookie del token de renovación
	 * @param propiedades   de ahí sale el plazo de inactividad que se informa al dashboard
	 */
	public SesionController(IniciarSesion iniciarSesion, RenovarSesion renovarSesion, CerrarSesion cerrarSesion,
			CookieRenovacion cookie, PropiedadesSeguridad propiedades) {
		this.iniciarSesion = iniciarSesion;
		this.renovarSesion = renovarSesion;
		this.cerrarSesion = cerrarSesion;
		this.cookie = cookie;
		this.inactividad = propiedades.sesion().inactividad();
	}

	/**
	 * Inicia sesión con RUN y contraseña. Es público (ver ConfiguracionSeguridad).
	 *
	 * @param solicitud RUN y contraseña
	 * @return 200 con el token y el usuario, más la cookie de renovación. 401 con el mismo mensaje para
	 *         cualquier falla
	 */
	@PostMapping("/login")
	public ResponseEntity<RespuestaLogin> login(@Valid @RequestBody SolicitudLogin solicitud,
			HttpServletRequest peticion) {
		return conSesion(iniciarSesion.iniciar(solicitud.run(), solicitud.contrasena(), peticion.getRemoteAddr()));
	}

	/**
	 * Entrega un token de acceso nuevo y reemplaza el de renovación. Es público: lo autoriza la cookie.
	 *
	 * @param token    token de renovación que trae la cookie; puede faltar
	 * @param peticion de ahí sale la IP, por si hay que auditar un token reutilizado
	 * @return 200 con el token y el usuario, más la cookie nueva. 401 si la sesión terminó
	 */
	@PostMapping("/renovar")
	public ResponseEntity<RespuestaLogin> renovar(
			@CookieValue(name = CookieRenovacion.NOMBRE, required = false) String token, HttpServletRequest peticion) {
		return conSesion(renovarSesion.renovar(token, peticion.getRemoteAddr()));
	}

	/**
	 * Cierra la sesión de este navegador. Responde igual aunque no haya sesión.
	 *
	 * @param token token de renovación que trae la cookie; puede faltar
	 * @return 204, con la orden de borrar la cookie
	 */
	@PostMapping("/salir")
	public ResponseEntity<Void> salir(@CookieValue(name = CookieRenovacion.NOMBRE, required = false) String token) {
		cerrarSesion.cerrar(token);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.borrada().toString()).build();
	}

	/**
	 * Quién tiene la sesión. Se lee del token, sin ir a la base.
	 *
	 * @param jwt token ya validado por Spring Security
	 * @return el usuario con sus permisos. 401 si no hay token o venció
	 */
	@GetMapping("/yo")
	public UsuarioRespuesta yo(@AuthenticationPrincipal Jwt jwt) {
		return UsuarioRespuesta.de(TokenJwt.leer(jwt));
	}

	/** @return 401 con el mensaje uniforme "RUN o contraseña incorrectos" */
	@ExceptionHandler(InicioSesionFallido.class)
	ProblemDetail inicioFallido(InicioSesionFallido fallo) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, fallo.getMessage());
	}

	/** @return 401 con el mensaje uniforme, y la orden de borrar la cookie que ya no sirve */
	@ExceptionHandler(SesionNoRenovable.class)
	ResponseEntity<ProblemDetail> sesionTerminada(SesionNoRenovable fallo) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.SET_COOKIE, cookie.borrada().toString())
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, fallo.getMessage()));
	}

	/** Respuesta común del login y de la renovación: el token en el cuerpo y la renovación en la cookie. */
	private ResponseEntity<RespuestaLogin> conSesion(SesionIniciada sesion) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.con(sesion.renovacion()).toString())
				.body(RespuestaLogin.de(sesion, inactividad));
	}

}
