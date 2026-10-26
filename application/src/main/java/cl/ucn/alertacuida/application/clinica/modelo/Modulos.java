package cl.ucn.alertacuida.application.clinica.modelo;

/**
 * Qué vigila el reloj en un paciente (columnas *_activa de la tabla configuracion).
 *
 * @param caidas      detección de caídas
 * @param fc          frecuencia cardíaca fuera de rango
 * @param spo2        saturación de oxígeno baja
 * @param inactividad inactividad prolongada
 * @param tendencia   tendencia anormal de las lecturas
 */
public record Modulos(boolean caidas, boolean fc, boolean spo2, boolean inactividad, boolean tendencia) {

	/** Los que rigen mientras el paciente no tiene configuración: todos menos la tendencia (como en V3). */
	public static final Modulos POR_DEFECTO = new Modulos(true, true, true, true, false);

}
