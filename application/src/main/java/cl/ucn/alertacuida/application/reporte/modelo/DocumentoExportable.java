package cl.ucn.alertacuida.application.reporte.modelo;

import java.util.List;

/**
 * Un reporte listo para exportar, independiente del formato: título, subtítulo y tablas de texto. Los adaptadores
 * lo dibujan como Excel (.xlsx) o PDF.
 *
 * @param titulo    ej. "Reporte semanal · María Soto"
 * @param subtitulo el período y la fecha de emisión
 * @param secciones las tablas, en orden
 */
public record DocumentoExportable(String titulo, String subtitulo, List<Seccion> secciones) {

	/** Copia la lista para que no cambie después. */
	public DocumentoExportable {
		secciones = List.copyOf(secciones);
	}

	/**
	 * Una tabla del reporte.
	 *
	 * @param titulo   ej. "Signos vitales"
	 * @param columnas los títulos de las columnas
	 * @param filas    cada fila con un texto por columna
	 */
	public record Seccion(String titulo, List<String> columnas, List<List<String>> filas) {

		/** Copia las listas para que no cambien después. */
		public Seccion {
			columnas = List.copyOf(columnas);
			filas = filas.stream().map(List::copyOf).toList();
		}

	}

}
