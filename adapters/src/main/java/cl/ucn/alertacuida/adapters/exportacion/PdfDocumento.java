package cl.ucn.alertacuida.adapters.exportacion;

import cl.ucn.alertacuida.application.reporte.modelo.DocumentoExportable;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;

/**
 * Dibuja un reporte como PDF, con OpenPDF: franja del gobierno, título en navy y cada tabla con su encabezado,
 * con los colores del sistema de diseño del PRLAC.
 */
public final class PdfDocumento {

	private static final Color NAVY = new Color(0x00, 0x33, 0x49);
	private static final Color GRIS = new Color(0xEF, 0xF3, 0xF5);
	private static final Color AZUL_GOBIERNO = new Color(0x00, 0x51, 0xA8);
	private static final Color ROJO_GOBIERNO = new Color(0xD6, 0x28, 0x28);

	private PdfDocumento() {
	}

	/**
	 * @param documento el reporte armado
	 * @return los bytes del PDF
	 */
	public static byte[] dibujar(DocumentoExportable documento) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document pdf = new Document(PageSize.A4, 36, 36, 36, 36);
		try {
			PdfWriter.getInstance(pdf, salida);
			pdf.open();
			pdf.add(franjaGobierno());
			Paragraph titulo = new Paragraph(documento.titulo(), new Font(Font.HELVETICA, 16, Font.BOLD, NAVY));
			titulo.setSpacingBefore(10);
			pdf.add(titulo);
			pdf.add(new Paragraph(documento.subtitulo(), new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY)));
			for (DocumentoExportable.Seccion seccion : documento.secciones()) {
				Paragraph nombre = new Paragraph(seccion.titulo(), new Font(Font.HELVETICA, 11, Font.BOLD, NAVY));
				nombre.setSpacingBefore(14);
				nombre.setSpacingAfter(6);
				pdf.add(nombre);
				pdf.add(tabla(seccion));
			}
			Paragraph pie = new Paragraph("AlertaCuida · Programa Red Local de Apoyos y Cuidados · Coquimbo",
					new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY));
			pie.setSpacingBefore(18);
			pdf.add(pie);
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo armar el PDF", e);
		} finally {
			pdf.close();
		}
		return salida.toByteArray();
	}

	/** @return la tabla de una sección, con el encabezado gris y letras navy */
	private static PdfPTable tabla(DocumentoExportable.Seccion seccion) {
		PdfPTable tabla = new PdfPTable(seccion.columnas().size());
		tabla.setWidthPercentage(100);
		Font encabezado = new Font(Font.HELVETICA, 8, Font.BOLD, NAVY);
		Font texto = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
		for (String columna : seccion.columnas()) {
			PdfPCell celda = new PdfPCell(new Phrase(columna.toUpperCase(), encabezado));
			celda.setBackgroundColor(GRIS);
			celda.setPadding(5);
			tabla.addCell(celda);
		}
		tabla.setHeaderRows(1);
		if (seccion.filas().isEmpty()) {
			PdfPCell vacia = new PdfPCell(new Phrase("Sin datos en el período", texto));
			vacia.setColspan(seccion.columnas().size());
			vacia.setPadding(5);
			tabla.addCell(vacia);
		}
		for (var fila : seccion.filas()) {
			for (String valor : fila) {
				PdfPCell celda = new PdfPCell(new Phrase(valor, texto));
				celda.setPadding(4);
				tabla.addCell(celda);
			}
		}
		return tabla;
	}

	/** @return la franja del Gobierno de Chile: 32 % azul, 68 % rojo */
	private static PdfPTable franjaGobierno() {
		PdfPTable franja = new PdfPTable(new float[] {32, 68});
		franja.setWidthPercentage(100);
		for (Color color : new Color[] {AZUL_GOBIERNO, ROJO_GOBIERNO}) {
			PdfPCell celda = new PdfPCell();
			celda.setBackgroundColor(color);
			celda.setFixedHeight(4);
			celda.setBorder(0);
			franja.addCell(celda);
		}
		return franja;
	}

}
