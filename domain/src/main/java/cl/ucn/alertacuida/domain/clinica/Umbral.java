package cl.ucn.alertacuida.domain.clinica;

import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;

/**
 * Rangos de una variable (tabla umbral): grave_bajo &lt; normal_min &lt; normal_max &lt; grave_alto.
 * Entre los límites normales la lectura es NORMAL; entre un límite normal y uno grave, LEVE; fuera
 * de los graves, GRAVE. La SpO2 no tiene límite alto: normalMax y graveAlto van vacíos.
 *
 * @param graveBajo bajo este valor la lectura es grave
 * @param normalMin desde este valor la lectura es normal
 * @param normalMax hasta este valor la lectura es normal (vacío en SpO2)
 * @param graveAlto sobre este valor la lectura es grave (vacío en SpO2)
 */
public record Umbral(BigDecimal graveBajo, BigDecimal normalMin, BigDecimal normalMax, BigDecimal graveAlto) {

	/**
	 * Valida y crea un umbral. Los valores se guardan sin ceros sobrantes (92.0 queda 92).
	 *
	 * @param variable FC o SPO2
	 * @return el umbral
	 * @throws IllegalArgumentException si falta un límite, sobra uno, no van en orden o alguno está
	 *                                  fuera de lo que el sensor puede medir
	 */
	public static Umbral de(TipoLectura variable, BigDecimal graveBajo, BigDecimal normalMin, BigDecimal normalMax,
			BigDecimal graveAlto) {
		if (graveBajo == null || normalMin == null) {
			throw new IllegalArgumentException("Faltan los límites bajos del umbral");
		}
		if (variable == TipoLectura.SPO2) {
			if (normalMax != null || graveAlto != null) {
				throw new IllegalArgumentException("La SpO2 no tiene límites altos");
			}
		} else if (normalMax == null || graveAlto == null) {
			throw new IllegalArgumentException("Faltan los límites altos del umbral");
		}
		for (BigDecimal limite : new BigDecimal[] {graveBajo, normalMin, normalMax, graveAlto}) {
			if (limite != null && !variable.admite(limite.doubleValue())) {
				throw new IllegalArgumentException("El límite " + limite.toPlainString()
						+ " está fuera de lo que mide el reloj");
			}
		}
		boolean enOrden = graveBajo.compareTo(normalMin) < 0
				&& (normalMax == null || (normalMin.compareTo(normalMax) < 0 && normalMax.compareTo(graveAlto) < 0));
		if (!enOrden) {
			throw new IllegalArgumentException(variable == TipoLectura.SPO2
					? "El límite grave debe ser menor que el mínimo normal"
					: "Los límites deben ir en orden: grave bajo < mínimo normal < máximo normal < grave alto");
		}
		return new Umbral(limpio(graveBajo), limpio(normalMin), limpio(normalMax), limpio(graveAlto));
	}

	/** @return el valor sin ceros sobrantes ni notación científica, o vacío */
	private static BigDecimal limpio(BigDecimal valor) {
		if (valor == null) {
			return null;
		}
		BigDecimal sinCeros = valor.stripTrailingZeros();
		return sinCeros.scale() < 0 ? sinCeros.setScale(0) : sinCeros;
	}

}
