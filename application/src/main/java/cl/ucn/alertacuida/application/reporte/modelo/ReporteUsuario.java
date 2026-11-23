package cl.ucn.alertacuida.application.reporte.modelo;

import java.time.LocalDate;

/**
 * El reporte de un usuario. Se calcula al pedirlo, no se guarda (modelo de datos), así que incluye las
 * lecturas que el reloj reenvió tarde.
 *
 * @param desde       primer día de Chile, incluido
 * @param hasta       último día de Chile, incluido
 * @param anterior    la semana anterior, para comparar; null en el diario
 */
public record ReporteUsuario(Periodo periodo, LocalDate desde, LocalDate hasta, IndicadoresPeriodo indicadores,
		IndicadoresPeriodo anterior) {
}
