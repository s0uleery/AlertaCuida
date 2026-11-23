package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.adapters.seguridad.dispositivo.PrincipalReloj;
import cl.ucn.alertacuida.application.avisoreloj.AcusarAvisoReloj;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Acuse de los avisos que el servidor le envió al reloj. Solo lo llama un reloj con su token. */
@RestController
@RequestMapping("/api/reloj/v1/avisos-fcm")
public class AvisosRelojController {

	private final AcusarAvisoReloj acusar;

	/** @param acusar caso de uso del acuse */
	public AvisosRelojController(AcusarAvisoReloj acusar) {
		this.acusar = acusar;
	}

	/**
	 * Cuerpo del acuse.
	 *
	 * @param fechaHoraRecepcion cuándo le llegó el aviso al reloj
	 */
	public record SolicitudAcuse(@NotNull Instant fechaHoraRecepcion) {
	}

	/** Confirma que llegó el aviso. 204 sin cuerpo, también al repetirlo; 404 si no es de este reloj. */
	@PostMapping("/{idAviso}/recepcion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void recepcion(@PathVariable UUID idAviso, @Valid @RequestBody SolicitudAcuse solicitud,
			@AuthenticationPrincipal PrincipalReloj principal) {
		acusar.acusar(principal.reloj(), idAviso, solicitud.fechaHoraRecepcion());
	}

}
