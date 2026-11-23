package cl.ucn.alertacuida.domain.alerta;

/**
 * Qué no calzó entre una alerta del reloj y la configuración vigente al recibirla
 * (discrepancia_alerta.codigo). La alerta se acepta igual: la discrepancia solo avisa al equipo.
 */
public enum CodigoDiscrepancia {

	/** El reloj la evaluó con una configuración distinta de la vigente (aún no descargaba la nueva). */
	VERSION_DISTINTA,
	/** Llegó una alerta de un módulo que el paciente tiene apagado. */
	MODULO_APAGADO,
	/** La gravedad no es la configurada (tendencia o zona segura). */
	GRAVEDAD_DISTINTA,
	/** Las lecturas de la evidencia no superan los umbrales vigentes del paciente. */
	LECTURAS_EN_RANGO

}
