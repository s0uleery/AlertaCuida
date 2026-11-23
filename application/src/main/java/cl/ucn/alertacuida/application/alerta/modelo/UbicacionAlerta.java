package cl.ucn.alertacuida.application.alerta.modelo;

import java.time.Instant;

/**
 * La posición GPS que mandó el reloj con una alerta.
 *
 * @param latitud           en grados
 * @param longitud          en grados
 * @param precisionMetros   radio de error; null si el reloj no lo informó
 * @param fechaHoraMedicion cuándo se tomó, en la hora del reloj
 */
public record UbicacionAlerta(double latitud, double longitud, Double precisionMetros, Instant fechaHoraMedicion) {
}
