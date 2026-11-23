package cl.ucn.alertacuida.adapters.web.paciente;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.RespuestaId;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.paciente.ConsultarPacientes;
import cl.ucn.alertacuida.application.paciente.DarDeBajaPaciente;
import cl.ucn.alertacuida.application.paciente.EditarPaciente;
import cl.ucn.alertacuida.application.paciente.RegistrarPaciente;
import cl.ucn.alertacuida.application.paciente.modelo.DetallePaciente;
import cl.ucn.alertacuida.application.paciente.modelo.ResumenPaciente;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pacientes del programa; en el dashboard se les llama "Usuarios". Usa los permisos del
 * módulo USUARIOS. Solo traduce HTTP: las reglas están en los casos de uso. El reloj de cada
 * paciente se maneja en {@code VinculoRelojController}.
 */
@RestController
@RequestMapping("/api/pacientes")
public class PacientesController {

	private static final String ENTIDAD = "PACIENTE";

	private final ConsultarPacientes consultar;
	private final RegistrarPaciente registrar;
	private final EditarPaciente editar;
	private final DarDeBajaPaciente baja;

	/** Recibe los casos de uso desde ConfiguracionCasosDeUso. */
	public PacientesController(ConsultarPacientes consultar, RegistrarPaciente registrar, EditarPaciente editar,
			DarDeBajaPaciente baja) {
		this.consultar = consultar;
		this.registrar = registrar;
		this.editar = editar;
		this.baja = baja;
	}

	/**
	 * @param incluirBajas si también se listan los que dejaron el programa (por defecto, no)
	 * @return los pacientes con su edad y reloj actual, ordenados por apellidos
	 */
	@GetMapping
	@PreAuthorize(Permisos.USUARIOS_VER)
	@LecturaAuditada(entidad = ENTIDAD)
	public List<ResumenPaciente> listar(@RequestParam(defaultValue = "false") boolean incluirBajas) {
		return consultar.listar(incluirBajas);
	}

	/** @return la ficha del paciente con su historial de relojes. 404 si no existe */
	@GetMapping("/{id}")
	@PreAuthorize(Permisos.USUARIOS_VER)
	@LecturaAuditada(entidad = ENTIDAD, idVariable = "id")
	public DetallePaciente detalle(@PathVariable UUID id) {
		return consultar.detalle(id);
	}

	/** @return 201 con el identificador. 400 si un dato no sirve, 409 si el RUN ya existe */
	@PostMapping
	@PreAuthorize(Permisos.USUARIOS_CREAR)
	@ResponseStatus(HttpStatus.CREATED)
	public RespuestaId registrar(@Valid @RequestBody SolicitudPaciente solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		UUID id = registrar.registrar(solicitud.datos(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
		return new RespuestaId(id);
	}

	/** Reemplaza la identidad del paciente. 204 sin cuerpo; 404 si no existe, 409 si el RUN es de otro. */
	@PutMapping("/{id}")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void editar(@PathVariable UUID id, @Valid @RequestBody SolicitudPaciente solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		editar.editar(id, solicitud.datos(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/** Da de baja con fecha de hoy y cierra su reloj. 204 sin cuerpo; 409 si ya estaba de baja. */
	@PostMapping("/{id}/baja")
	@PreAuthorize(Permisos.USUARIOS_ELIMINAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void darDeBaja(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		baja.darDeBaja(id, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

}
