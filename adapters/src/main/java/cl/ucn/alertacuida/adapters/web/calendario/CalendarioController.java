package cl.ucn.alertacuida.adapters.web.calendario;

import cl.ucn.alertacuida.application.calendario.ConsultarCalendario;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calendario hábil para el dashboard, de solo lectura. Lo ve todo el equipo: basta con iniciar
 * sesión, sin un permiso en particular.
 */
@RestController
@RequestMapping("/api/calendario")
public class CalendarioController {

	private final ConsultarCalendario consultar;

	/** Recibe el caso de uso; lo arma ConfiguracionCalendario. */
	public CalendarioController(ConsultarCalendario consultar) {
		this.consultar = consultar;
	}

	/** @return el horario de cada día hábil y los feriados. 401 sin sesión */
	@GetMapping
	public RespuestaCalendario consultar() {
		return RespuestaCalendario.de(consultar.consultar());
	}

}
