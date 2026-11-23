package cl.ucn.alertacuida.adapters.web.clinica;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.clinica.PerfilClinicoPaciente;
import cl.ucn.alertacuida.application.clinica.modelo.PerfilClinico;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil clínico de un paciente: módulos, GPS, modo conservador, espera entre contactos, umbrales
 * y zona segura. Ver exige USUARIOS:VER; cambiar, USUARIOS:EDITAR.
 */
@RestController
@RequestMapping("/api/pacientes/{idPaciente}/perfil-clinico")
public class PerfilClinicoController {

	private final PerfilClinicoPaciente perfil;

	/** @param perfil caso de uso del perfil clínico */
	public PerfilClinicoController(PerfilClinicoPaciente perfil) {
		this.perfil = perfil;
	}

	/** @return lo que rige hoy, con los valores base donde no hay ajustes propios. 404 si no existe */
	@GetMapping
	@PreAuthorize(Permisos.USUARIOS_VER)
	@LecturaAuditada(entidad = "CONFIGURACION", idVariable = "idPaciente")
	public PerfilClinico consultar(@PathVariable UUID idPaciente) {
		return perfil.consultar(idPaciente);
	}

	/** Reemplaza módulos y opciones. 204 sin cuerpo; 400 si un dato no sirve. */
	@PutMapping("/ajustes")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cambiarAjustes(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudAjustes solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		perfil.cambiarAjustes(idPaciente, solicitud.ajustes(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/** Ajusta el umbral de FC o SPO2. 204 sin cuerpo; 400 si los límites no van en orden. */
	@PutMapping("/umbrales/{variable}")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void ajustarUmbral(@PathVariable UUID idPaciente, @PathVariable TipoLectura variable,
			@Valid @RequestBody SolicitudUmbral solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		perfil.ajustarUmbral(idPaciente, variable, solicitud.graveBajo(), solicitud.normalMin(),
				solicitud.normalMax(), solicitud.graveAlto(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/**
	 * Define o cambia la zona segura (centro, radio y severidad). No la activa: eso va en los ajustes.
	 * 204 sin cuerpo; 400 si el centro o el radio no sirven.
	 */
	@PutMapping("/zona-segura")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void definirZona(@PathVariable UUID idPaciente, @Valid @RequestBody SolicitudZona solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		perfil.definirZona(idPaciente, solicitud.latitud(), solicitud.longitud(), solicitud.radioMetros(),
				solicitud.severidad(), TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/** Vuelve al umbral base sin borrar el historial. 204 sin cuerpo. */
	@DeleteMapping("/umbrales/{variable}")
	@PreAuthorize(Permisos.USUARIOS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void volverAlBase(@PathVariable UUID idPaciente, @PathVariable TipoLectura variable,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		perfil.volverAlUmbralBase(idPaciente, variable, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

}
