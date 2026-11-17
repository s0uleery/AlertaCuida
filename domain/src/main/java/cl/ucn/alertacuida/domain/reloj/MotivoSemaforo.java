package cl.ucn.alertacuida.domain.reloj;

/** Por qué el semáforo de un reloj no está verde. El dashboard muestra cada motivo al pasar el mouse. */
public enum MotivoSemaforo {
	/** Nadie lo usa (gris). */
	SIN_VINCULAR,
	/** Lleva el plazo del silencio de datos sin sincronizar (rojo). */
	SIN_SENAL,
	/** El plan de datos de su SIM venció (rojo). */
	PLAN_VENCIDO,
	/** La última sincronización informó batería bajo el mínimo (amarillo). */
	BATERIA_BAJA,
	/** La última sincronización, de día, informó que no está puesto (amarillo). */
	NO_PUESTO,
	/** El reloj todavía no aplica la configuración vigente del paciente (amarillo). */
	CONFIGURACION_PENDIENTE,
	/** Tiene una alerta técnica sin cerrar (amarillo). */
	ALERTA_TECNICA_ABIERTA,
	/** Se atrasó en sincronizar, sin llegar aún al silencio de datos (amarillo). */
	ATRASO,
	/** Su app es más antigua que la más nueva del programa (amarillo). */
	VERSION_ANTIGUA,
	/** El plan de datos de su SIM vence pronto (amarillo). */
	PLAN_POR_VENCER;

	/** @return el color que impone este motivo */
	public ColorSemaforo color() {
		return switch (this) {
			case SIN_VINCULAR -> ColorSemaforo.GRIS;
			case SIN_SENAL, PLAN_VENCIDO -> ColorSemaforo.ROJO;
			default -> ColorSemaforo.AMARILLO;
		};
	}

}
