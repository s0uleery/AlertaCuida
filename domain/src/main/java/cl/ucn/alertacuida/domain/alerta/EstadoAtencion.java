package cl.ucn.alertacuida.domain.alerta;

/**
 * Qué ha hecho el equipo PRLAC con una alerta (vista alerta_estado.estado_atencion). No se guarda: se
 * calcula con la atención registrada.
 */
public enum EstadoAtencion {
	SIN_ASIGNAR,
	EN_ATENCION,
	EN_SEGUIMIENTO,
	RESUELTA
}
