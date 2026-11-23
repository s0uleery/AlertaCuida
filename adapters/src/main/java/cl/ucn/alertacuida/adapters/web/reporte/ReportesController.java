package cl.ucn.alertacuida.adapters.web.reporte;

import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.reporte.ConsultarReportes;
import cl.ucn.alertacuida.application.reporte.modelo.ReporteUsuario;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes diarios y semanales de un usuario, calculados al pedirlos. */
@RestController
@RequestMapping("/api/pacientes/{id}/reportes")
public class ReportesController {

	private static final String ENTIDAD = "REPORTE";

	private final ConsultarReportes consultar;

	/** @param consultar caso de uso de los reportes */
	public ReportesController(ConsultarReportes consultar) {
		this.consultar = consultar;
	}

	/**
	 * @param dia día de Chile (AAAA-MM-DD); si falta, hoy
	 * @return el reporte de ese día. 404 si el usuario no existe
	 */
	@GetMapping("/diario")
	@PreAuthorize(Permisos.REPORTES_VER)
	@LecturaAuditada(entidad = ENTIDAD, idVariable = "id")
	public ReporteUsuario diario(@PathVariable UUID id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
		return consultar.diario(id, dia);
	}

	/**
	 * @param dia cualquier día de la semana pedida; si falta, hoy
	 * @return el reporte de esa semana de lunes a domingo, con la anterior para comparar. 404 si el usuario no
	 *         existe
	 */
	@GetMapping("/semanal")
	@PreAuthorize(Permisos.REPORTES_VER)
	@LecturaAuditada(entidad = ENTIDAD, idVariable = "id")
	public ReporteUsuario semanal(@PathVariable UUID id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
		return consultar.semanal(id, dia);
	}

}
