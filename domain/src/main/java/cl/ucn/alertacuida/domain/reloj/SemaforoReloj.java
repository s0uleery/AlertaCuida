package cl.ucn.alertacuida.domain.reloj;

import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Calcula el semáforo de un reloj a partir de lo que ya envía (decisiones del 13-11 y del 16-11-2026):
 * <ul>
 * <li>gris si nadie lo usa;</li>
 * <li>rojo si lleva el plazo del silencio de datos sin sincronizar (desde el vínculo si nunca sincronizó) o si el
 * plan de datos de su SIM venció;</li>
 * <li>amarillo si la última sincronización informó batería baja o, de día, que no está puesto; si tiene la
 * configuración pendiente o una alerta técnica abierta; si se atrasó en sincronizar; si su app es más antigua
 * que la más nueva del programa; o si su plan vence pronto;</li>
 * <li>verde si nada de lo anterior.</li>
 * </ul>
 * El color sale de los datos, no de si la alerta técnica correspondiente sigue abierta. Trae todos los motivos.
 */
public final class SemaforoReloj {

	private SemaforoReloj() {
	}

	/**
	 * @param ahora el momento en que se mira
	 * @return el color (el más grave de los motivos) y todos los motivos
	 */
	public static EstadoSemaforo evaluar(DatosSemaforo datos, UmbralesSemaforo umbrales, Instant ahora) {
		Set<MotivoSemaforo> motivos = EnumSet.noneOf(MotivoSemaforo.class);
		revisarPlan(datos.vencimientoPlan(), umbrales, ahora, motivos);
		if (!datos.vinculado()) {
			motivos.add(MotivoSemaforo.SIN_VINCULAR);
			return new EstadoSemaforo(ColorSemaforo.GRIS, List.copyOf(motivos));
		}
		revisarSenal(datos, umbrales, ahora, motivos);
		revisarUltimaSincronizacion(datos, umbrales, motivos);
		if (datos.configuracionPendiente()) {
			motivos.add(MotivoSemaforo.CONFIGURACION_PENDIENTE);
		}
		if (datos.alertaTecnicaAbierta()) {
			motivos.add(MotivoSemaforo.ALERTA_TECNICA_ABIERTA);
		}
		if (esMasAntigua(datos.versionApp(), datos.versionMasNueva())) {
			motivos.add(MotivoSemaforo.VERSION_ANTIGUA);
		}
		ColorSemaforo color = motivos.stream().map(MotivoSemaforo::color)
				.max(Comparator.comparingInt(SemaforoReloj::gravedad)).orElse(ColorSemaforo.VERDE);
		return new EstadoSemaforo(color, List.copyOf(motivos));
	}

	/** Sin señal desde el plazo del silencio; antes de eso, atraso si pasó el plazo del atraso. */
	private static void revisarSenal(DatosSemaforo datos, UmbralesSemaforo umbrales, Instant ahora,
			Set<MotivoSemaforo> motivos) {
		Instant referencia = datos.ultimaSincronizacion() != null ? datos.ultimaSincronizacion()
				: datos.inicioVinculo();
		if (referencia == null) {
			return;
		}
		if (!ahora.isBefore(referencia.plus(umbrales.silencio()))) {
			motivos.add(MotivoSemaforo.SIN_SENAL);
		} else if (ahora.isAfter(referencia.plus(umbrales.atraso()))) {
			motivos.add(MotivoSemaforo.ATRASO);
		}
	}

	/** Batería baja y, si fue de día en Chile, reloj no puesto. Solo mira la última sincronización. */
	private static void revisarUltimaSincronizacion(DatosSemaforo datos, UmbralesSemaforo umbrales,
			Set<MotivoSemaforo> motivos) {
		if (datos.nivelBateria() != null && datos.nivelBateria() < umbrales.bateriaMinima()) {
			motivos.add(MotivoSemaforo.BATERIA_BAJA);
		}
		if (Boolean.FALSE.equals(datos.relojPuesto()) && datos.ultimaSincronizacion() != null) {
			int hora = datos.ultimaSincronizacion().atZone(ZonaHoraria.CHILE).getHour();
			if (hora >= umbrales.horaInicioDia() && hora < umbrales.horaFinDia()) {
				motivos.add(MotivoSemaforo.NO_PUESTO);
			}
		}
	}

	/** Vencido si ya pasó su último día; por vencer si vence dentro de los días de aviso (incluido hoy). */
	private static void revisarPlan(LocalDate vencimiento, UmbralesSemaforo umbrales, Instant ahora,
			Set<MotivoSemaforo> motivos) {
		if (vencimiento == null) {
			return;
		}
		LocalDate hoy = LocalDate.ofInstant(ahora, ZonaHoraria.CHILE);
		if (vencimiento.isBefore(hoy)) {
			motivos.add(MotivoSemaforo.PLAN_VENCIDO);
		} else if (!vencimiento.isAfter(hoy.plusDays(umbrales.diasAvisoPlan()))) {
			motivos.add(MotivoSemaforo.PLAN_POR_VENCER);
		}
	}

	/**
	 * @return si la versión es menor que la más nueva, comparando número a número ("1.2" es menor que "1.10", y
	 *         "1.10" es igual a "1.10.0"); sin alguna de las dos, no
	 */
	static boolean esMasAntigua(String version, String masNueva) {
		if (version == null || masNueva == null) {
			return false;
		}
		return compararVersiones(version, masNueva) < 0;
	}

	/** @return negativo, cero o positivo según la primera versión sea menor, igual o mayor que la segunda */
	public static int compararVersiones(String una, String otra) {
		String[] a = una.trim().split("\\.");
		String[] b = otra.trim().split("\\.");
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int diferencia = Long.compare(numero(a, i), numero(b, i));
			if (diferencia != 0) {
				return diferencia;
			}
		}
		return 0;
	}

	/** @return el número de esa parte de la versión, sin lo que no sean dígitos; 0 si no existe */
	private static long numero(String[] partes, int i) {
		if (i >= partes.length) {
			return 0;
		}
		String digitos = partes[i].replaceAll("\\D", "");
		return digitos.isEmpty() ? 0 : Long.parseLong(digitos.length() > 18 ? digitos.substring(0, 18) : digitos);
	}

	/** @return cuánto pesa cada color para elegir el más grave */
	private static int gravedad(ColorSemaforo color) {
		return switch (color) {
			case VERDE -> 0;
			case AMARILLO -> 1;
			case ROJO -> 2;
			case GRIS -> 3;
		};
	}

}
