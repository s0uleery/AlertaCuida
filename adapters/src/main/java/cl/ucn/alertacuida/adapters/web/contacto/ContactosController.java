package cl.ucn.alertacuida.adapters.web.contacto;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.RespuestaId;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.contacto.ContactosPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.TipoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
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
 * Contactos de un paciente y el catálogo de tipos. Ver exige USUARIOS:VER; agregar, editar,
 * ordenar, desactivar y registrar el opt-in, USUARIOS:EDITAR (son parte de su ficha).
 */
@RestController
@RequestMapping("/api")
public class ContactosController {

	private final ContactosPaciente contactos;

	/** @param contactos caso de uso de los contactos del paciente */
	public ContactosController(ContactosPaciente contactos) {
		this.contactos = contactos;
	}

	/**
	 * Cuerpo de {@code PUT …/contactos/{id}/opt-in}.
	 *
	 * @param aceptado true si la persona aceptó recibir WhatsApp; false si lo revocó
	 */
	public record SolicitudOptIn(@NotNull Boolean aceptado) {
	}

	/**
	 * Cuerpo de {@code PUT …/contactos/orden}.
	 *
	 * @param gravedad la lista que se ordena: LEVE, MEDIA o GRAVE
	 * @param orden    todos los contactos de esa gravedad, del primero al último
	 */
	public record SolicitudOrden(@NotNull Severidad gravedad, @NotNull List<@NotNull UUID> orden) {
	}

	/**
	 * Cuerpo de {@code POST …/contactos/{id}/reactivar}.
	 *
	 * @param gravedades de qué alertas vuelve a recibir avisos (al menos una)
	 */
	public record SolicitudReactivar(@NotEmpty Set<@NotNull Severidad> gravedades) {
	}

	/** @return los tipos de contacto que se pueden elegir */
	@GetMapping("/tipos-contacto")
	@PreAuthorize(Permisos.USUARIOS_VER)
	public List<TipoContacto> tipos() {
		return contactos.tipos();
	}

	/** @return la lista del paciente con las horas sin cobertura. 404 si el paciente no existe */
	@GetMapping("/pacientes/{idPaciente}/contactos")
	@PreAuthorize(Permisos.USUARIOS_VER)
	@LecturaAuditada(entidad = "CONTACTO_PACIENTE", idVariable = "idPaciente")
	public RespuestaContactos listar(@PathVariable UUID idPaciente) {
		return RespuestaContactos.de(contactos.listar(idPaciente));
	}

	/** @return 201 con el identificador del contacto. 400 si un dato no sirve, 409 si ya es contacto */
	@PostMapping("/pacientes/{idPaciente}/contactos")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.CREATED)
	public RespuestaId agregar(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudContacto solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		return new RespuestaId(contactos.agregar(idPaciente, solicitud.datos(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr()));
	}

	/**
	 * Cambia el orden en que avisa el reloj en las alertas de una gravedad. 204 sin cuerpo; 400 si no están
	 * todos los contactos de esa gravedad.
	 */
	@PutMapping("/pacientes/{idPaciente}/contactos/orden")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reordenar(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudOrden solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		contactos.reordenar(idPaciente, solicitud.gravedad(), solicitud.orden(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
	}

	/** Reemplaza los datos del contacto. 204 sin cuerpo; 404 si es de otro paciente, 409 si choca. */
	@PutMapping("/pacientes/{idPaciente}/contactos/{idContacto}")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void editar(@PathVariable UUID idPaciente, @PathVariable UUID idContacto,
			@Valid @RequestBody SolicitudContacto solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		contactos.editar(idPaciente, idContacto, solicitud.datos(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
	}

	/** Registra que aceptó o revocó recibir WhatsApp. 204 sin cuerpo; 400 si no tiene WhatsApp. */
	@PutMapping("/pacientes/{idPaciente}/contactos/{idContacto}/opt-in")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void optIn(@PathVariable UUID idPaciente, @PathVariable UUID idContacto,
			@Valid @RequestBody SolicitudOptIn solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		contactos.registrarOptIn(idPaciente, idContacto, solicitud.aceptado(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
	}

	/** Saca al contacto de la lista sin borrarlo. 204 sin cuerpo; 409 si era el único de alguna gravedad. */
	@PostMapping("/pacientes/{idPaciente}/contactos/{idContacto}/desactivar")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void desactivar(@PathVariable UUID idPaciente, @PathVariable UUID idContacto,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		contactos.desactivar(idPaciente, idContacto, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/** Vuelve a poner al contacto al final de cada gravedad elegida. 204 sin cuerpo; 400 sin gravedades. */
	@PostMapping("/pacientes/{idPaciente}/contactos/{idContacto}/reactivar")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reactivar(@PathVariable UUID idPaciente, @PathVariable UUID idContacto,
			@Valid @RequestBody SolicitudReactivar solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		contactos.reactivar(idPaciente, idContacto, solicitud.gravedades(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
	}

}
