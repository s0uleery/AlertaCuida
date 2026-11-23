package cl.ucn.alertacuida.application.reporte.modelo;

/**
 * Cuánto se usó el reloj en el período.
 *
 * @param minutosPuesto    suma de los minutos puesto que informó cada sincronización
 * @param sincronizaciones cuántos lotes llegaron
 */
public record UsoReloj(int minutosPuesto, int sincronizaciones) {
}
