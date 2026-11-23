package cl.ucn.alertacuida.application.redseguridad.modelo;

import cl.ucn.alertacuida.domain.clinica.Umbral;
import java.time.Duration;
import java.time.Instant;

/**
 * Qué lecturas revisar y cómo.
 *
 * @param desde    primera hora del rango, incluida
 * @param hasta    fin del rango, excluido
 * @param ventana  distancia máxima entre la lectura y una alerta del reloj para darla por alertada
 * @param baseFc   umbral de FC cuando el paciente no tiene uno propio vigente al medir
 * @param baseSpo2 umbral de SpO2 cuando el paciente no tiene uno propio vigente al medir
 * @param limite   cuántos hallazgos como máximo
 */
public record CriterioHallazgos(Instant desde, Instant hasta, Duration ventana, Umbral baseFc, Umbral baseSpo2,
		int limite) {
}
