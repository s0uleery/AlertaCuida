package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.personal.ConsultarEquipo;
import cl.ucn.alertacuida.application.personal.GuardarPermisosRol;
import cl.ucn.alertacuida.application.personal.modelo.RolConPermisos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles del personal y sus permisos (pestaña Permisos de Administración). Solo traduce HTTP.
 * Ver exige ADMINISTRACION:VER y cambiar, ADMINISTRACION:EDITAR.
 */
@RestController
@RequestMapping("/api/roles")
public class RolesController {

	private final ConsultarEquipo consultar;
	private final GuardarPermisosRol guardarPermisos;

	/**
	 * @param consultar       lista de roles con sus permisos
	 * @param guardarPermisos caso de uso que reemplaza los permisos de un rol
	 */
	public RolesController(ConsultarEquipo consultar, GuardarPermisosRol guardarPermisos) {
		this.consultar = consultar;
		this.guardarPermisos = guardarPermisos;
	}

	/** @return los roles activos con sus permisos ("MODULO:ACCION"), el administrador primero */
	@GetMapping
	@PreAuthorize(Permisos.ADMINISTRACION_VER)
	public List<RolConPermisos> listar() {
		return consultar.roles();
	}

	/**
	 * Deja al rol con exactamente esos permisos. Rigen cuando cada persona vuelve a iniciar sesión.
	 *
	 * @param codigo rol que se edita
	 * @return 204. 400 si un permiso no existe, 404 si el rol no existe, 409 si es el administrador
	 */
	@PutMapping("/{codigo}/permisos")
	@PreAuthorize(Permisos.ADMINISTRACION_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void guardarPermisos(@PathVariable String codigo, @Valid @RequestBody SolicitudPermisos solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		// HashSet y no Set.copyOf: un null en la lista debe llegar al caso de uso como permiso inválido (400)
		guardarPermisos.guardar(codigo, new HashSet<>(solicitud.permisos()), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
	}

}
