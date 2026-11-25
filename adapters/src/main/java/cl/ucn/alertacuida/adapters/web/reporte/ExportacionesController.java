package cl.ucn.alertacuida.adapters.web.reporte;

import cl.ucn.alertacuida.adapters.exportacion.ExcelDocumento;
import cl.ucn.alertacuida.adapters.exportacion.PdfDocumento;
import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.reporte.ExportarReportes;
import cl.ucn.alertacuida.application.reporte.modelo.DocumentoExportable;
import cl.ucn.alertacuida.application.reporte.modelo.Periodo;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportación de los reportes a Excel (.xlsx) o PDF desde Reportería (decisión del 25-11-2026). Exige
 * REPORTES:EXPORTAR y cada descarga queda en la auditoría como EXPORTAR REPORTE.
 */
@RestController
public class ExportacionesController {

	private static final MediaType XLSX =
			MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ExportarReportes exportar;

	/** @param exportar arma los reportes */
	public ExportacionesController(ExportarReportes exportar) {
		this.exportar = exportar;
	}

	/**
	 * Reporte diario o semanal de un usuario.
	 *
	 * @param periodo "diario" o "semanal"
	 * @param formato "xlsx" o "pdf"
	 * @param dia     el día (o cualquiera de la semana); si falta, hoy
	 * @return el archivo. 404 si el usuario no existe o el período o el formato no existen
	 */
	@GetMapping("/api/pacientes/{id}/reportes/{periodo}.{formato}")
	@PreAuthorize(Permisos.REPORTES_EXPORTAR)
	public ResponseEntity<byte[]> usuario(@PathVariable UUID id, @PathVariable String periodo,
			@PathVariable String formato,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		Periodo elegido = switch (periodo) {
			case "diario" -> Periodo.DIARIO;
			case "semanal" -> Periodo.SEMANAL;
			default -> throw new NoEncontrado("No existe el reporte " + periodo);
		};
		Formato tipo = Formato.de(formato);
		DocumentoExportable documento = exportar.usuario(id, elegido, dia, TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
		return archivo(documento, "reporte-" + periodo, tipo);
	}

	/**
	 * Gestión operativa del equipo en un período.
	 *
	 * @param formato "xlsx" o "pdf"
	 * @param desde   primer día
	 * @param hasta   último día
	 * @return el archivo. 400 si el período está al revés o es muy largo
	 */
	@GetMapping("/api/reportes/gestion.{formato}")
	@PreAuthorize(Permisos.REPORTES_EXPORTAR)
	public ResponseEntity<byte[]> gestion(@PathVariable String formato,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest peticion) {
		Formato tipo = Formato.de(formato);
		DocumentoExportable documento = exportar.gestion(desde, hasta, TokenJwt.leer(jwt).id(),
				peticion.getRemoteAddr());
		return archivo(documento, "gestion-" + desde + "-" + hasta, tipo);
	}

	/** @return el documento dibujado en ese formato, como descarga */
	private static ResponseEntity<byte[]> archivo(DocumentoExportable documento, String nombre, Formato formato) {
		byte[] bytes = formato == Formato.XLSX ? ExcelDocumento.dibujar(documento) : PdfDocumento.dibujar(documento);
		return ResponseEntity.ok()
				.contentType(formato == Formato.XLSX ? XLSX : MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename("alertacuida-" + nombre + "." + formato.extension).build().toString())
				.body(bytes);
	}

	/** Los formatos que se pueden pedir. */
	private enum Formato {
		XLSX("xlsx"),
		PDF("pdf");

		private final String extension;

		Formato(String extension) {
			this.extension = extension;
		}

		/** @throws NoEncontrado si no es xlsx ni pdf */
		static Formato de(String texto) {
			for (Formato formato : values()) {
				if (formato.extension.equals(texto.toLowerCase(Locale.ROOT))) {
					return formato;
				}
			}
			throw new NoEncontrado("No se exporta a " + texto);
		}
	}

}
