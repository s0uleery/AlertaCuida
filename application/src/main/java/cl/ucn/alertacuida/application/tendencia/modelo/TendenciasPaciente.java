package cl.ucn.alertacuida.application.tendencia.modelo;

import java.time.Instant;
import java.util.List;

/**
 * La evolución de FC y SpO2 de un paciente en un rango, con sus alertas.
 *
 * @param desde   comienzo del rango, incluido
 * @param hasta   fin del rango, excluido
 * @param fc      en orden de fecha
 * @param spo2    en orden de fecha
 * @param alertas     sin simulacros, en orden de detección
 * @param ubicaciones las posiciones GPS, en orden; vacía si el reloj no tiene GPS activo
 */
public record TendenciasPaciente(Agrupacion agrupacion, Instant desde, Instant hasta, List<PuntoTendencia> fc,
		List<PuntoTendencia> spo2, List<MarcaAlerta> alertas, List<UbicacionTendencia> ubicaciones) {
}
