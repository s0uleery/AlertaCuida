package cl.ucn.alertacuida.application.telemetria.modelo;

import java.time.Instant;

/**
 * Una posición GPS del reloj (solo si el paciente tiene el GPS activo). No genera alertas.
 *
 * @param precisionMetros opcional
 */
public record UbicacionRecibida(double latitud, double longitud, Double precisionMetros, Instant medicion) {
}
