package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.personal.CrearCuentaPersonal;
import cl.ucn.alertacuida.application.personal.NuevoEnlaceActivacion;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuentas del personal del PRLAC. Solo traduce HTTP: las reglas están en los casos de uso.
 * Todos los endpoints exigen ADMINISTRACION:CREAR, que solo tiene el administrador.
 */
@RestController
@RequestMapping("/api/personal")
public class PersonalController {

	private final CrearCuentaPersonal crearCuenta;
	private final NuevoEnlaceActivacion nuevoEnlace;
	private final String urlActivacion;

	/**
	 * @param crearCuenta caso de uso del alta
	 * @param nuevoEnlace caso de uso para reemplazar un enlace perdido o vencido
	 * @param propiedades de ahí sale la dirección de la página de activación del dashboard
	 */
	public PersonalController(CrearCuentaPersonal crearCuenta, NuevoEnlaceActivacion nuevoEnlace,
			PropiedadesSeguridad propiedades) {
		this.crearCuenta = crearCuenta;
		this.nuevoEnlace = nuevoEnlace;
		this.urlActivacion = propiedades.activacion().url();
	}

	/**
	 * Crea la cuenta de una persona del equipo, inactiva hasta que use su enlace.
	 *
	 * @param solicitud datos de la persona y su rol
	 * @param jwt       token del administrador, para la auditoría
	 * @return 201 con el enlace de activación. 400 si un dato no sirve, 409 si el RUN o el correo ya existen
	 */
	@PostMapping
	@PreAuthorize(Permisos.ADMINISTRACION_CREAR)
	@ResponseStatus(HttpStatus.CREATED)
	public RespuestaEnlace crear(@Valid @RequestBody SolicitudCuenta solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		return respuesta(crearCuenta.crear(solicitud.datos(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr()));
	}

	/**
	 * Emite otro enlace para una cuenta pendiente. El anterior deja de servir.
	 *
	 * @param id cuenta pendiente
	 * @return el enlace nuevo. 404 si la cuenta no existe o ya está activa
	 */
	@PostMapping("/{id}/enlace-activacion")
	@PreAuthorize(Permisos.ADMINISTRACION_CREAR)
	public RespuestaEnlace nuevoEnlace(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		return respuesta(nuevoEnlace.emitir(id, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr()));
	}

	/** El token va después de "#": esa parte no se envía al servidor, así no queda en ningún log. */
	private RespuestaEnlace respuesta(EnlaceActivacion enlace) {
		return new RespuestaEnlace(enlace.idUsuario(), urlActivacion + "#" + enlace.token(), enlace.expiraEn());
	}

}
