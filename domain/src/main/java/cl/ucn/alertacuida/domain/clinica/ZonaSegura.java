package cl.ucn.alertacuida.domain.clinica;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Zona segura de un paciente (tabla zona_segura): un círculo alrededor de un punto, por lo general
 * su domicilio. El reloj compara su GPS con el círculo y, si la persona sale, envía una alerta
 * ZONA_SEGURA con la severidad de la zona.
 *
 * @param latitud     del centro, en grados (-90 a 90)
 * @param longitud    del centro, en grados (-180 a 180)
 * @param radioMetros distancia desde el centro que se considera dentro de la zona
 * @param severidad   gravedad de la alerta al salir
 */
public record ZonaSegura(BigDecimal latitud, BigDecimal longitud, int radioMetros, Severidad severidad) {

	/** Radio mínimo: por debajo, el error del GPS haría saltar alertas estando la persona en casa. */
	public static final int RADIO_MINIMO = 50;
	/** Radio máximo, para que la zona siga siendo el entorno de la persona y no toda la comuna. */
	public static final int RADIO_MAXIMO = 5000;
	/** Severidad cuando no se elige otra. */
	public static final Severidad SEVERIDAD_POR_DEFECTO = Severidad.MEDIA;

	private static final BigDecimal LATITUD_MAXIMA = BigDecimal.valueOf(90);
	private static final BigDecimal LONGITUD_MAXIMA = BigDecimal.valueOf(180);
	/** Seis decimales son unos 10 cm: más precisión no sirve con un GPS de reloj. */
	private static final int DECIMALES = 6;

	/**
	 * Valida y crea una zona. Las coordenadas se guardan con a lo más seis decimales y sin ceros sobrantes.
	 *
	 * @param severidad vacía = {@link #SEVERIDAD_POR_DEFECTO}
	 * @return la zona
	 * @throws IllegalArgumentException si falta el centro, está fuera del mapa o el radio no está
	 *                                  entre {@value #RADIO_MINIMO} y {@value #RADIO_MAXIMO} metros
	 */
	public static ZonaSegura de(BigDecimal latitud, BigDecimal longitud, Integer radioMetros, Severidad severidad) {
		if (latitud == null || longitud == null) {
			throw new IllegalArgumentException("Falta el centro de la zona segura");
		}
		if (latitud.abs().compareTo(LATITUD_MAXIMA) > 0) {
			throw new IllegalArgumentException("La latitud debe estar entre -90 y 90");
		}
		if (longitud.abs().compareTo(LONGITUD_MAXIMA) > 0) {
			throw new IllegalArgumentException("La longitud debe estar entre -180 y 180");
		}
		if (radioMetros == null || radioMetros < RADIO_MINIMO || radioMetros > RADIO_MAXIMO) {
			throw new IllegalArgumentException("El radio debe estar entre " + RADIO_MINIMO + " y " + RADIO_MAXIMO
					+ " metros");
		}
		return new ZonaSegura(limpia(latitud), limpia(longitud), radioMetros,
				severidad == null ? SEVERIDAD_POR_DEFECTO : severidad);
	}

	/** @return la coordenada con a lo más seis decimales, sin ceros sobrantes ni notación científica */
	private static BigDecimal limpia(BigDecimal coordenada) {
		BigDecimal sinCeros = coordenada.setScale(Math.min(coordenada.scale(), DECIMALES), RoundingMode.HALF_UP)
				.stripTrailingZeros();
		return sinCeros.scale() < 0 ? sinCeros.setScale(0) : sinCeros;
	}

}
