package cl.ucn.alertacuida.application.tendencia.modelo;

import java.time.Instant;

/**
 * Una posición GPS del usuario para el mapa de las tendencias. Con {@link Agrupacion#HORA} o
 * {@link Agrupacion#DIA} es la última de esa hora o de ese día.
 *
 * @param precisionMetros   radio de error; null si el reloj no lo informó
 * @param fechaHoraMedicion la hora del reloj al tomarla
 */
public record UbicacionTendencia(double latitud, double longitud, Double precisionMetros,
		Instant fechaHoraMedicion) {
}
