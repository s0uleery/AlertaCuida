package cl.ucn.alertacuida.adapters.web.redseguridad;

import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.redseguridad.ConsultarHallazgos;
import cl.ucn.alertacuida.application.redseguridad.modelo.Hallazgo;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Red de seguridad: posibles eventos que el reloj no alertó, para el informe del coordinador. */
@RestController
@RequestMapping("/api/hallazgos")
public class HallazgosController {

	private final ConsultarHallazgos consultar;

	/** @param consultar caso de uso de la red de seguridad */
	public HallazgosController(ConsultarHallazgos consultar) {
		this.consultar = consultar;
	}

	/**
	 * @param desde primer día de Chile (AAAA-MM-DD), incluido; si falta, 6 días antes de hasta
	 * @param hasta último día de Chile, incluido; si falta, hoy
	 * @return las lecturas más allá del límite grave sin alerta del reloj cerca, la más reciente primero. 400 si
	 *         el rango está al revés o pasa de 31 días
	 */
	@GetMapping
	@PreAuthorize(Permisos.REPORTES_VER)
	@LecturaAuditada(entidad = "REPORTE")
	public List<Hallazgo> listar(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return consultar.listar(desde, hasta);
	}

}
