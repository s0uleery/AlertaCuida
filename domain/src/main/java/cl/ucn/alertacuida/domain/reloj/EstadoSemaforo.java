package cl.ucn.alertacuida.domain.reloj;

import java.util.List;

/**
 * El semáforo de un reloj.
 *
 * @param color   el más grave de sus motivos; verde si no tiene
 * @param motivos todos los que aplican, en el orden de {@link MotivoSemaforo}
 */
public record EstadoSemaforo(ColorSemaforo color, List<MotivoSemaforo> motivos) {

	/** Copia la lista para que nadie la cambie después. */
	public EstadoSemaforo {
		motivos = List.copyOf(motivos);
	}

}
