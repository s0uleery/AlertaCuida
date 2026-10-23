package cl.ucn.alertacuida.adapters.web.reloj;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.RespuestaId;
import cl.ucn.alertacuida.application.reloj.ConsultarRelojes;
import cl.ucn.alertacuida.application.reloj.EditarReloj;
import cl.ucn.alertacuida.application.reloj.RegistrarReloj;
import cl.ucn.alertacuida.application.reloj.modelo.RelojConPaciente;
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

/** Inventario de relojes. Usa los permisos del módulo USUARIOS. */
@RestController
@RequestMapping("/api/relojes")
public class RelojesController {

	private final ConsultarRelojes consultar;
	private final RegistrarReloj registrar;
	private final EditarReloj editar;

	/** Recibe los casos de uso desde ConfiguracionCasosDeUso. */
	public RelojesController(ConsultarRelojes consultar, RegistrarReloj registrar, EditarReloj editar) {
		this.consultar = consultar;
		this.registrar = registrar;
		this.editar = editar;
	}

	/** @return todos los relojes con el paciente que usa cada uno */
	@GetMapping
	@PreAuthorize(Permisos.USUARIOS_VER)
	public List<RelojConPaciente> listar() {
		return consultar.listar();
	}

	/** @return 201 con el identificador. 409 si el identificador o la SIM ya son de otro reloj */
	@PostMapping
	@PreAuthorize(Permisos.USUARIOS_CREAR)
	@ResponseStatus(HttpStatus.CREATED)
	public RespuestaId registrar(@Valid @RequestBody SolicitudReloj solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		UUID id = registrar.registrar(solicitud.datos(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
		return new RespuestaId(id);
	}

	/** Reemplaza los datos del reloj. 204 sin cuerpo; 409 si se saca de servicio un reloj en uso. */
	@PutMapping("/{id}")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void editar(@PathVariable UUID id, @Valid @RequestBody SolicitudReloj solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		editar.editar(id, solicitud.datos(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

}
