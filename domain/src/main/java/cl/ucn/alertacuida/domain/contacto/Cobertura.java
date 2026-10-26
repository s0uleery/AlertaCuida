package cl.ucn.alertacuida.domain.contacto;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Horas del día en que ningún contacto puede recibir avisos. Las alertas medias y graves avisan a
 * toda hora, así que cada paciente debería tener a alguien disponible las 24 horas.
 */
public final class Cobertura {

	private static final int MINUTOS_DIA = 24 * 60;

	/** Solo métodos estáticos: no se crean objetos de esta clase. */
	private Cobertura() {
	}

	/**
	 * Un tramo sin cobertura, en hora de Chile. Si el inicio es posterior al fin, cruza la
	 * medianoche; si son iguales, es el día completo.
	 *
	 * @param inicio desde esta hora, incluida
	 * @param fin    hasta esta hora, sin incluirla
	 */
	public record Tramo(LocalTime inicio, LocalTime fin) {
	}

	/**
	 * @param horarios horarios de los contactos disponibles; vacío (null) = todo el día
	 * @return los tramos sin nadie disponible, ordenados por hora de inicio; vacío si está cubierto todo el día
	 */
	public static List<Tramo> sinCubrir(List<Horario> horarios) {
		boolean[] cubierto = new boolean[MINUTOS_DIA];
		for (Horario horario : horarios) {
			for (int minuto = 0; minuto < MINUTOS_DIA; minuto++) {
				if (horario == null || horario.cubre(LocalTime.MIN.plusMinutes(minuto))) {
					cubierto[minuto] = true;
				}
			}
		}
		List<int[]> huecos = new ArrayList<>();
		int minuto = 0;
		while (minuto < MINUTOS_DIA) {
			if (cubierto[minuto]) {
				minuto++;
				continue;
			}
			int inicio = minuto;
			while (minuto < MINUTOS_DIA && !cubierto[minuto]) {
				minuto++;
			}
			huecos.add(new int[] {inicio, minuto});
		}
		if (huecos.size() == 1 && huecos.getFirst()[0] == 0 && huecos.getFirst()[1] == MINUTOS_DIA) {
			return List.of(new Tramo(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT));
		}
		// Un hueco que llega a la medianoche y otro que parte en ella son uno solo que la cruza
		if (huecos.size() > 1 && huecos.getFirst()[0] == 0 && huecos.getLast()[1] == MINUTOS_DIA) {
			huecos.getLast()[1] = huecos.removeFirst()[1];
		}
		return huecos.stream()
				.map(hueco -> new Tramo(LocalTime.MIN.plusMinutes(hueco[0]), LocalTime.MIN.plusMinutes(hueco[1])))
				.toList();
	}

}
