package cl.ucn.alertacuida.application.respuesta.modelo;

/**
 * Lo que registra la oficina PRLAC al llamar al reloj del paciente (mensaje N1-02). Los nombres son los de la
 * columna atencion_alerta.resultado_llamada.
 */
public enum ResultadoLlamadaOficina {

	/** La persona está bien: confirma la alerta y falta decir si el evento ocurrió. */
	BIEN,

	/** La persona necesita que alguien vaya. */
	NECESITA_ATENCION,

	/** La persona no contestó: también hay que ir. */
	NO_CONTESTA

}
