package cl.ucn.alertacuida.adapters.web.tendencia;

import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.tendencia.ConsultarTendencias;
import cl.ucn.alertacuida.application.tendencia.modelo.TendenciasPaciente;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tendencias de FC y SpO2 de un usuario, para los gráficos de la sección Reportes. */
@RestController
public class TendenciasController {

	private final ConsultarTendencias consultar;

	/** @param consultar caso de uso de las tendencias */
	public TendenciasController(ConsultarTendencias consultar) {
		this.consultar = consultar;
	}

	/**
	 * @param desde primer día de Chile (AAAA-MM-DD), incluido; si falta, 6 días antes de hasta
	 * @param hasta último día de Chile, incluido; si falta, hoy
	 * @return lecturas juntas según el largo del rango, y las alertas para marcarlas. 400 si el rango está al
	 *         revés o pasa de 90 días; 404 si el usuario no existe
	 */
	@GetMapping("/api/pacientes/{id}/tendencias")
	@PreAuthorize(Permisos.REPORTES_VER)
	@LecturaAuditada(entidad = "REPORTE", idVariable = "id")
	public TendenciasPaciente consultar(@PathVariable UUID id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return consultar.consultar(id, desde, hasta);
	}

}
