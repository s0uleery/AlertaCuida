package cl.ucn.alertacuida.application.alerta.modelo;

import java.time.Instant;
import java.util.List;

/**
 * Lo que explica una alerta, tal como se muestra en su ficha. Sale de alerta.evidencia: el reloj manda
 * lecturas, batería, inmovilidad y ubicación; las técnicas del servidor guardan el dato que las disparó.
 * Cada campo es null si esa alerta no lo trae. La celda de la matriz no se muestra (decisión del 19-11-2026).
 *
 * @param lecturas             las que llevaron a alertar, en el orden en que llegaron; vacía si no trae
 * @param nivelBateria         en %, del reloj o de la alerta técnica de batería baja
 * @param segundosInmovilidad  en las caídas
 * @param ubicacion            la posición GPS al detectar la alerta
 * @param alertasEnVentana     exceso de alertas: cuántas llegaron en la ventana
 * @param ventanaMinutos       exceso de alertas: el largo de la ventana
 * @param noPuestoDesde        reloj no puesto: desde cuándo lo informa
 * @param ultimaSincronizacion silencio de datos: la última vez que sincronizó; null también si nunca lo hizo
 */
public record EvidenciaAlerta(List<LecturaEvidencia> lecturas, Integer nivelBateria, Integer segundosInmovilidad,
		UbicacionAlerta ubicacion, Integer alertasEnVentana, Integer ventanaMinutos, Instant noPuestoDesde,
		Instant ultimaSincronizacion) {

	/** Una alerta sin evidencia, o con una evidencia que no se pudo leer. */
	public static final EvidenciaAlerta VACIA = new EvidenciaAlerta(List.of(), null, null, null, null, null, null,
			null);

	/** Copia la lista para que el registro no cambie después. */
	public EvidenciaAlerta {
		lecturas = List.copyOf(lecturas);
	}

}
