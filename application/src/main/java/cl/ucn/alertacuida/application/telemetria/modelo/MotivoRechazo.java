package cl.ucn.alertacuida.application.telemetria.modelo;

/** Por qué se descartó una lectura del lote. */
public enum MotivoRechazo {
	/** Valor que el sensor no puede medir. */
	FUERA_DE_RANGO,
	/** Hora posterior a la del servidor, con más de 5 minutos de tolerancia. */
	HORA_FUTURA,
	/** Medida antes de que el reloj se entregara a este paciente: es de otra persona. */
	ANTERIOR_A_LA_ASIGNACION
}
