package cl.ucn.alertacuida.adapters.web.alerta;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.alerta.AtenderAlerta;
import cl.ucn.alertacuida.application.alerta.ConsultarAlertas;
import cl.ucn.alertacuida.application.alerta.modelo.DetalleAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.ResumenAlerta;
import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alertas en el dashboard. Ver exige ALERTAS:VER; asumir, resolver y cerrar el seguimiento, ALERTAS:EDITAR. */
@RestController
@RequestMapping("/api/alertas")
public class AlertasController {

	private static final String ENTIDAD = "ALERTA";

	private final ConsultarAlertas consultar;
	private final AtenderAlerta atender;

	/**
	 * @param consultar caso de uso de la lista y el detalle
	 * @param atender   caso de uso de lo que hace el equipo con una alerta
	 */
	public AlertasController(ConsultarAlertas consultar, AtenderAlerta atender) {
		this.consultar = consultar;
		this.atender = atender;
	}

	/**
	 * Cuerpo de {@code POST /api/alertas/{id}/resolver}.
	 *
	 * @param clasificacion obligatoria en las alertas de salud; vacía en las técnicas
	 * @param observaciones obligatorias en toda alerta
	 */
	public record SolicitudResolucion(ClasificacionAlerta clasificacion, String observaciones) {
	}

	/** @return las alertas no canceladas: primero las que quedaron sin respuesta, después las más recientes */
	@GetMapping
	@PreAuthorize(Permisos.ALERTAS_VER)
	@LecturaAuditada(entidad = ENTIDAD)
	public List<ResumenAlerta> listar() {
		return consultar.listar();
	}

	/** @return la alerta con su línea de tiempo. 404 si no existe */
	@GetMapping("/{id}")
	@PreAuthorize(Permisos.ALERTAS_VER)
	@LecturaAuditada(entidad = ENTIDAD, idVariable = "id")
	public DetalleAlerta detalle(@PathVariable UUID id) {
		return consultar.detalle(id);
	}

	/** Toma el caso. 204 sin cuerpo; 404 si no existe; 409 si ya tiene responsable, terminó o se canceló. */
	@PostMapping("/{id}/asumir")
	@PreAuthorize(Permisos.ALERTAS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void asumir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		atender.asumir(id, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/** Cierra la alerta. 204 sin cuerpo; 400 si falta un dato; 404 si no existe; 409 si ya terminó o se canceló. */
	@PostMapping("/{id}/resolver")
	@PreAuthorize(Permisos.ALERTAS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resolver(@PathVariable UUID id, @RequestBody SolicitudResolucion solicitud,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		atender.resolver(id, solicitud.clasificacion(), solicitud.observaciones(), TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
	}

	/** Termina el seguimiento de una alerta POSITIVA. 204 sin cuerpo; 404 si no existe; 409 si no tiene uno abierto. */
	@PostMapping("/{id}/seguimiento/cerrar")
	@PreAuthorize(Permisos.ALERTAS_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cerrarSeguimiento(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		atender.cerrarSeguimiento(id, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

}
