package cl.ucn.alertacuida.adapters.web.condicion;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.RespuestaId;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.condicion.CondicionesPaciente;
import cl.ucn.alertacuida.application.condicion.modelo.Condicion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enfermedades y situaciones de un paciente. Ver exige USUARIOS:VER; agregar y quitar,
 * USUARIOS:EDITAR (son parte de su ficha).
 */
@RestController
@RequestMapping("/api/pacientes/{idPaciente}/condiciones")
public class CondicionesController {

	private final CondicionesPaciente condiciones;

	/** @param condiciones caso de uso de las condiciones del paciente */
	public CondicionesController(CondicionesPaciente condiciones) {
		this.condiciones = condiciones;
	}

	/** @return enfermedades y luego situaciones, por orden alfabético. 404 si el paciente no existe */
	@GetMapping
	@PreAuthorize(Permisos.USUARIOS_VER)
	@LecturaAuditada(entidad = "CONDICION_PACIENTE", idVariable = "idPaciente")
	public List<Condicion> listar(@PathVariable UUID idPaciente) {
		return condiciones.listar(idPaciente);
	}

	/** @return 201 con el identificador. 400 si la descripción no sirve, 409 si ya la tiene */
	@PostMapping
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.CREATED)
	public RespuestaId agregar(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudCondicion solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		UUID id = condiciones.agregar(idPaciente, solicitud.tipo(), solicitud.descripcion(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
		return new RespuestaId(id);
	}

	/** Quita la condición. 204 sin cuerpo; 404 si no existe o es de otro paciente. */
	@DeleteMapping("/{idCondicion}")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void quitar(@PathVariable UUID idPaciente, @PathVariable UUID idCondicion,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		condiciones.quitar(idPaciente, idCondicion, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

}
