package cl.ucn.alertacuida.adapters.exportacion;

import cl.ucn.alertacuida.application.reporte.modelo.DocumentoExportable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/** Dibuja un reporte como planilla Excel (.xlsx), con fastexcel: una hoja con el título y cada tabla debajo. */
public final class ExcelDocumento {

	/** Color de los encabezados: el navy del PRLAC. */
	private static final String NAVY = "003349";

	private ExcelDocumento() {
	}

	/**
	 * @param documento el reporte armado
	 * @return los bytes del .xlsx
	 */
	public static byte[] dibujar(DocumentoExportable documento) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try {
			Workbook libro = new Workbook(salida, "AlertaCuida", "1.0");
			Worksheet hoja = libro.newWorksheet("Reporte");
			int fila = 0;
			hoja.value(fila, 0, documento.titulo());
			hoja.style(fila, 0).bold().fontSize(14).fontColor(NAVY).set();
			fila++;
			hoja.value(fila, 0, documento.subtitulo());
			fila += 2;
			for (DocumentoExportable.Seccion seccion : documento.secciones()) {
				hoja.value(fila, 0, seccion.titulo());
				hoja.style(fila, 0).bold().fontColor(NAVY).set();
				fila++;
				for (int c = 0; c < seccion.columnas().size(); c++) {
					hoja.value(fila, c, seccion.columnas().get(c));
					hoja.style(fila, c).bold().fontColor("FFFFFF").fillColor(NAVY).set();
				}
				fila++;
				for (var celdas : seccion.filas()) {
					for (int c = 0; c < celdas.size(); c++) {
						hoja.value(fila, c, celdas.get(c));
					}
					fila++;
				}
				fila++;
			}
			for (int c = 0; c < 7; c++) {
				hoja.width(c, c == 0 ? 34 : 20);
			}
			libro.finish();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo armar la planilla", e);
		}
		return salida.toByteArray();
	}

}
