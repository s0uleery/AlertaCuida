package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.adapters.seguridad.dispositivo.PrincipalReloj;
import cl.ucn.alertacuida.application.telemetria.RecibirSincronizacion;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sincronización del reloj cada 2 horas. Solo la llama un reloj con su token (lo exige la cadena
 * de seguridad de /api/reloj); el reloj nunca dice de qué paciente es.
 */
@RestController
@RequestMapping("/api/reloj/v1/sincronizaciones")
public class SincronizacionesController {

	private final RecibirSincronizacion recibir;

	/** @param recibir caso de uso que guarda el lote */
	public SincronizacionesController(RecibirSincronizacion recibir) {
		this.recibir = recibir;
	}

	/**
	 * @return 200 con lo guardado, lo repetido y lo descartado (también si el lote ya había llegado).
	 *         400 si está mal formado, 413 si es muy grande, 409 si el eventId es de otro reloj
	 */
	@PostMapping
	public RespuestaSincronizacion sincronizar(@Valid @RequestBody SolicitudSincronizacion solicitud,
			@AuthenticationPrincipal PrincipalReloj principal) {
		return RespuestaSincronizacion.de(recibir.recibir(principal.reloj(), solicitud.lote()));
	}

}
