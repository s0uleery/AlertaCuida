package cl.ucn.alertacuida.application.clinica.modelo;

/**
 * Qué vigila el reloj en un paciente (columnas *_activa y boton_ayuda_activo de la tabla configuracion).
 *
 * @param caidas      detección de caídas
 * @param fc          frecuencia cardíaca fuera de rango
 * @param spo2        saturación de oxígeno baja
 * @param inactividad inactividad prolongada
 * @param tendencia   tiempo fuera del rango normal, sin llegar al límite grave
 * @param zonaSegura  salida del círculo de la zona segura; exige GPS y una zona guardada
 * @param botonAyuda  botón del reloj para pedir ayuda, que avisa primero a los vecinos
 */
public record Modulos(boolean caidas, boolean fc, boolean spo2, boolean inactividad, boolean tendencia,
		boolean zonaSegura, boolean botonAyuda) {

	/** Los que rigen mientras el paciente no tiene configuración (como en V3, V8 y V9): todos menos la zona. */
	public static final Modulos POR_DEFECTO = new Modulos(true, true, true, true, true, false, true);

}
