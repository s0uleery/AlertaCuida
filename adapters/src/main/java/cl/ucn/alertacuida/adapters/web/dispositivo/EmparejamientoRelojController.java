package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.application.asignacion.EmparejarReloj;
import cl.ucn.alertacuida.application.asignacion.modelo.CodigoEmparejamiento;
import cl.ucn.alertacuida.application.asignacion.modelo.EstadoEmparejamiento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emparejamiento del reloj con código. Son las únicas rutas de la API del reloj que no llevan token:
 * el reloj todavía no tiene uno que sirva (ver ConfiguracionSeguridad). El código lo escribe después
 * quien instala, en el dashboard ({@code POST /api/pacientes/{id}/reloj} con codigoEmparejamiento).
 */
@RestController
@RequestMapping("/api/reloj/v1/emparejamientos")
public class EmparejamientoRelojController {

	private final EmparejarReloj emparejar;

	/** @param emparejar caso de uso del emparejamiento */
	public EmparejamientoRelojController(EmparejarReloj emparejar) {
		this.emparejar = emparejar;
	}

	/**
	 * Cuerpo de la consulta. El token va en el cuerpo y no en Authorization porque, mientras está
	 * pendiente, la cadena de seguridad del reloj lo rechazaría con 401.
	 *
	 * @param token el que recibió el reloj al pedir el código
	 */
	public record SolicitudConsulta(@NotBlank @Size(max = 200) String token) {
	}

	/** @param estado PENDIENTE (seguir preguntando), VINCULADO (ya puede usar su token) o VENCIDO */
	public record RespuestaConsulta(EstadoEmparejamiento estado) {
	}

	/**
	 * Entrega un código nuevo para mostrar en pantalla y el token que usará el reloj.
	 *
	 * @return 201 con el código, el token, su vencimiento y cada cuánto consultar; 429
	 *         DEMASIADAS_PETICIONES si hay demasiados códigos pendientes
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CodigoEmparejamiento iniciar() {
		return emparejar.iniciar();
	}

	/** @return 200 con el estado del emparejamiento de ese token */
	@PostMapping("/consulta")
	public RespuestaConsulta consultar(@Valid @RequestBody SolicitudConsulta solicitud) {
		return new RespuestaConsulta(emparejar.consultar(solicitud.token()));
	}

}
