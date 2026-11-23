package cl.ucn.alertacuida.application.reporte.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.Map;

/**
 * Los indicadores de un usuario en un período (decisión del 19-11-2026). Son apoyo para el profesional, no
 * diagnóstico.
 *
 * @param fc                 frecuencia cardíaca
 * @param spo2               saturación de oxígeno
 * @param uso                minutos puesto y sincronizaciones
 * @param bateriaMinima      la batería más baja informada; null si no llegó ninguna sincronización
 * @param alertasPorTipo     sin simulacros ni canceladas; solo los tipos que tuvieron alguna
 * @param alertasPorGravedad con la gravedad actual (GRAVE si se agravó); mismas alertas que por tipo
 * @param alertasCanceladas  las que el paciente canceló en el reloj, sin simulacros
 */
public record IndicadoresPeriodo(Estadistica fc, Estadistica spo2, UsoReloj uso, Integer bateriaMinima,
		Map<TipoEvento, Integer> alertasPorTipo, Map<Severidad, Integer> alertasPorGravedad, int alertasCanceladas) {
}
