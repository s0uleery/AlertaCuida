package cl.ucn.alertacuida.adapters.web.asignacion;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.asignacion.VinculoReloj;
import cl.ucn.alertacuida.application.asignacion.modelo.TokenReloj;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El reloj de un paciente (1.2.3 y 1.2.4): vincular, reemplazar, desvincular y token nuevo.
 * Todo exige USUARIOS:EDITAR. Las respuestas con token lo muestran una sola vez.
 */
@RestController
@RequestMapping("/api/pacientes/{idPaciente}/reloj")
public class VinculoRelojController {

	private final VinculoReloj vinculo;

	/** @param vinculo caso de uso del vínculo reloj-paciente */
	public VinculoRelojController(VinculoReloj vinculo) {
		this.vinculo = vinculo;
	}

	/**
	 * Entrega un reloj a un paciente que no tiene.
	 *
	 * @return 201 con el token del reloj. 400 sin consentimiento; 404 si algo no existe; 409 si el
	 *         paciente ya tiene reloj, está de baja, o el reloj no está disponible
	 */
	@PostMapping
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.CREATED)
	public TokenReloj vincular(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudVinculo solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		return vinculo.vincular(idPaciente, solicitud.idDispositivo(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
	}

	/**
	 * Cambia el reloj del paciente por otro. El token del anterior deja de servir.
	 *
	 * @return el token del reloj nuevo. 409 si el paciente no tiene reloj o el nuevo no está disponible
	 */
	@PostMapping("/reemplazo")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	public TokenReloj reemplazar(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudVinculo solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		return vinculo.reemplazar(idPaciente, solicitud.idDispositivo(), solicitud.estadoRelojAnterior(),
				TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/** Retira el reloj al paciente; su token deja de servir. 204 sin cuerpo; 409 si no tenía. */
	@DeleteMapping
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void desvincular(@PathVariable UUID idPaciente, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		vinculo.desvincular(idPaciente, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/** @return otro token para el mismo reloj; el anterior deja de servir. 409 si no tiene reloj */
	@PostMapping("/token")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	public TokenReloj nuevoToken(@PathVariable UUID idPaciente, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		return vinculo.nuevoToken(idPaciente, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

}
