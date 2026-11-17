package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.personal.CambiarRecibeAvisos;
import cl.ucn.alertacuida.application.personal.CambiarRolPersonal;
import cl.ucn.alertacuida.application.personal.ConsultarEquipo;
import cl.ucn.alertacuida.application.personal.CrearCuentaPersonal;
import cl.ucn.alertacuida.application.personal.NuevoEnlaceActivacion;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuentas del personal del PRLAC. Solo traduce HTTP: las reglas están en los casos de uso.
 * Ver exige ADMINISTRACION:VER; crear, ADMINISTRACION:CREAR; cambiar, ADMINISTRACION:EDITAR. Hoy
 * los tres los tiene solo el administrador.
 */
@RestController
@RequestMapping("/api/personal")
public class PersonalController {

	private final CrearCuentaPersonal crearCuenta;
	private final NuevoEnlaceActivacion nuevoEnlace;
	private final ConsultarEquipo consultar;
	private final CambiarRolPersonal cambiarRol;
	private final CambiarRecibeAvisos cambiarAvisos;
	private final String urlActivacion;

	/**
	 * @param crearCuenta   caso de uso del alta
	 * @param nuevoEnlace   caso de uso para reemplazar un enlace perdido o vencido
	 * @param consultar     lista de las cuentas del equipo
	 * @param cambiarRol    caso de uso del cambio de rol
	 * @param cambiarAvisos caso de uso del aviso técnico por correo
	 * @param propiedades   de ahí sale la dirección de la página de activación del dashboard
	 */
	public PersonalController(CrearCuentaPersonal crearCuenta, NuevoEnlaceActivacion nuevoEnlace,
			ConsultarEquipo consultar, CambiarRolPersonal cambiarRol, CambiarRecibeAvisos cambiarAvisos,
			PropiedadesSeguridad propiedades) {
		this.crearCuenta = crearCuenta;
		this.nuevoEnlace = nuevoEnlace;
		this.consultar = consultar;
		this.cambiarRol = cambiarRol;
		this.cambiarAvisos = cambiarAvisos;
		this.urlActivacion = propiedades.activacion().url();
	}

	/** @return las cuentas del equipo por nombre, con su estado, último acceso y aviso técnico por correo */
	@GetMapping
	@PreAuthorize(Permisos.ADMINISTRACION_VER)
	public List<RespuestaPerfil> listar() {
		return consultar.perfiles().stream().map(RespuestaPerfil::de).toList();
	}

	/**
	 * Cambia el rol de otra cuenta. Rige cuando la persona vuelve a iniciar sesión.
	 *
	 * @param id        cuenta que se cambia
	 * @param solicitud rol nuevo y, al pasar a COORDINADOR sin teléfono, el teléfono
	 * @return 204. 400 si el rol no existe o falta el teléfono, 404 si la cuenta no existe, 409 si es
	 *         la propia o dejaría el sistema sin administrador activo
	 */
	@PutMapping("/{id}/rol")
	@PreAuthorize(Permisos.ADMINISTRACION_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cambiarRol(@PathVariable UUID id, @Valid @RequestBody SolicitudCambioRol solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		cambiarRol.cambiar(id, solicitud.cambio(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/**
	 * Enciende o apaga el correo de las alertas técnicas de una cuenta, también la propia.
	 *
	 * @param id cuenta que se cambia
	 * @return 204. 404 si la cuenta no existe
	 */
	@PutMapping("/{id}/recibe-avisos")
	@PreAuthorize(Permisos.ADMINISTRACION_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cambiarRecibeAvisos(@PathVariable UUID id, @Valid @RequestBody SolicitudRecibeAvisos solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		cambiarAvisos.cambiar(id, solicitud.recibeAvisos(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
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
