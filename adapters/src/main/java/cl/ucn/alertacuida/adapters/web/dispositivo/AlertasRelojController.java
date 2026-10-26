package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.adapters.seguridad.dispositivo.PrincipalReloj;
import cl.ucn.alertacuida.application.alerta.RecibirAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.RespuestaAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.ResultadoAlerta;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Alertas que envía el reloj, las que canceló el paciente y la consulta de su estado. Solo las
 * llama un reloj con su token. Las alertas se identifican por el eventId del reloj.
 */
@RestController
@RequestMapping("/api/reloj/v1")
public class AlertasRelojController {

	private final RecibirAlerta recibir;
	private final ObjectMapper json;

	/**
	 * @param recibir caso de uso de las alertas del reloj
	 * @param json    convierte la evidencia a JSON para guardarla tal cual
	 */
	public AlertasRelojController(RecibirAlerta recibir, ObjectMapper json) {
		this.recibir = recibir;
		this.json = json;
	}

	/** @return 201 con la alerta y su estado; 200 si ya había llegado (reintento). 400 si está mal formada */
	@PostMapping("/alertas")
	public ResponseEntity<RespuestaAlerta> crear(@Valid @RequestBody SolicitudAlerta solicitud,
			@AuthenticationPrincipal PrincipalReloj principal) {
		AlertaRecibida alerta = new AlertaRecibida(solicitud.eventId(), solicitud.tipoEvento(), solicitud.severidad(),
				solicitud.versionConfiguracion(), solicitud.fechaHoraDeteccion(), solicitud.fechaHoraEnvio(),
				solicitud.esperaDiaHabil(), null, evidencia(solicitud.evidencia()));
		return respuesta(recibir.recibir(principal.reloj(), alerta));
	}

	/** @return 201 con la alerta cancelada; 200 si ya había llegado. 409 si ese eventId se envió como alerta */
	@PostMapping("/cancelaciones")
	public ResponseEntity<RespuestaAlerta> cancelar(@Valid @RequestBody SolicitudCancelacion solicitud,
			@AuthenticationPrincipal PrincipalReloj principal) {
		AlertaRecibida cancelacion = new AlertaRecibida(solicitud.eventId(), solicitud.tipoEvento(),
				solicitud.severidad(), solicitud.versionConfiguracion(), solicitud.fechaHoraDeteccion(), null, false,
				solicitud.fechaHoraCancelacion(), evidencia(solicitud.evidencia()));
		return respuesta(recibir.registrarCancelacion(principal.reloj(), cancelacion));
	}

	/** @return el estado de la alerta. 404 si no hay una con ese eventId de este reloj */
	@GetMapping("/alertas/{eventIdAlerta}")
	public EstadoAlerta estado(@PathVariable UUID eventIdAlerta, @AuthenticationPrincipal PrincipalReloj principal) {
		return recibir.estado(principal.reloj(), eventIdAlerta);
	}

	/** @return 201 si la alerta es nueva, 200 si el reloj la reenvió; el mismo cuerpo en ambos casos */
	private static ResponseEntity<RespuestaAlerta> respuesta(ResultadoAlerta resultado) {
		HttpStatus estado = resultado.nueva() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(estado).body(resultado.respuesta());
	}

	/** @return la evidencia como texto JSON, o null si no vino */
	private String evidencia(EvidenciaReloj evidencia) {
		return evidencia == null ? null : json.writeValueAsString(evidencia);
	}

}
