package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.adapters.seguridad.dispositivo.PrincipalReloj;
import cl.ucn.alertacuida.application.escalamiento.SolicitarAviso;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Escalamiento dirigido por el reloj: el reloj pide avisar a cada contacto y el servidor lo envía. Solo
 * traduce HTTP; las reglas están en {@link SolicitarAviso}.
 */
@RestController
@RequestMapping("/api/reloj/v1")
public class EscalamientoRelojController {

	private final SolicitarAviso solicitar;

	/** @param solicitar caso de uso de la solicitud de aviso */
	public EscalamientoRelojController(SolicitarAviso solicitar) {
		this.solicitar = solicitar;
	}

	/**
	 * Pide avisar a un contacto por WhatsApp o correo. Un rechazo no es un error: responde 200 con el motivo.
	 *
	 * @return 200 ACEPTADO o RECHAZADO, con el estado de la alerta. 400 MENSAJE_INVALIDO si falta un dato o
	 *         la ronda no es 1 ni 2; 404 ALERTA_DESCONOCIDA si la alerta no es de este reloj; 409 NO_APLICA si
	 *         el eventId ya se usó para un aviso de otra alerta
	 */
	@PostMapping("/alertas/{eventIdAlerta}/avisos")
	public RespuestaAvisoReloj solicitarAviso(@PathVariable UUID eventIdAlerta,
			@Valid @RequestBody SolicitudAvisoReloj solicitud, @AuthenticationPrincipal PrincipalReloj principal) {
		return RespuestaAvisoReloj.de(solicitar.solicitar(principal.reloj(), eventIdAlerta, solicitud.solicitud()));
	}

}
