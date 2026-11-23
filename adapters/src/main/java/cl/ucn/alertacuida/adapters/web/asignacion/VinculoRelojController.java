package cl.ucn.alertacuida.adapters.web.asignacion;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.asignacion.VinculoReloj;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * El reloj de un paciente: vincular, reemplazar, emparejar de nuevo y desvincular. Todo exige USUARIOS:EDITAR.
 * Se vincula siempre con el código de emparejamiento que muestra el reloj: ninguna respuesta trae el token del
 * reloj, que nadie escribe ni copia.
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
	 * Cuerpo de {@code POST .../reloj/emparejamiento}.
	 *
	 * @param codigoEmparejamiento el que muestra el reloj
	 */
	public record SolicitudEmparejamiento(@NotBlank @Size(max = 20) String codigoEmparejamiento) {
	}

	/** @param idAsignacion el vínculo reloj-paciente creado o emparejado */
	public record RespuestaVinculo(UUID idAsignacion) {
	}

	/**
	 * Entrega un reloj a un paciente que no tiene.
	 *
	 * @return 201 con el vínculo. 400 sin consentimiento, sin código o con un código que no existe o venció;
	 *         404 si algo no existe; 409 si el paciente ya tiene reloj, está de baja, o el reloj no está
	 *         disponible
	 */
	@PostMapping
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.CREATED)
	public RespuestaVinculo vincular(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudVinculo solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		return new RespuestaVinculo(vinculo.vincular(idPaciente, solicitud.idDispositivo(),
				solicitud.codigoEmparejamiento(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr()));
	}

	/**
	 * Cambia el reloj del paciente por otro. El token del anterior deja de servir.
	 *
	 * @return el vínculo nuevo. 400 sin código o con uno que no existe o venció; 409 si el paciente no tiene
	 *         reloj o el nuevo no está disponible
	 */
	@PostMapping("/reemplazo")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	public RespuestaVinculo reemplazar(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudVinculo solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		return new RespuestaVinculo(vinculo.reemplazar(idPaciente, solicitud.idDispositivo(),
				solicitud.estadoRelojAnterior(), solicitud.codigoEmparejamiento(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr()));
	}

	/**
	 * Empareja de nuevo el mismo reloj (por ejemplo, tras restaurarlo de fábrica o si su token se filtró) con el
	 * código que muestra. Su token anterior deja de servir; el vínculo sigue igual.
	 *
	 * @return el vínculo. 400 si el código no existe o venció; 409 si no tiene reloj
	 */
	@PostMapping("/emparejamiento")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	public RespuestaVinculo emparejar(@PathVariable UUID idPaciente,
			@Valid @RequestBody SolicitudEmparejamiento solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		return new RespuestaVinculo(vinculo.emparejar(idPaciente, solicitud.codigoEmparejamiento(),
				TokenJwt.leer(jwt).id(), peticion.getRemoteAddr()));
	}

	/** Retira el reloj al paciente; su token deja de servir. 204 sin cuerpo; 409 si no tenía. */
	@DeleteMapping
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void desvincular(@PathVariable UUID idPaciente, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		vinculo.desvincular(idPaciente, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

}
