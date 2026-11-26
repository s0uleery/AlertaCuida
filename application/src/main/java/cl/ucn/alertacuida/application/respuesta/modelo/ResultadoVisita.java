package cl.ucn.alertacuida.application.respuesta.modelo;

/**
 * Cómo encontró el personal a la persona al llegar a su domicilio (mensaje N1-09). Los nombres son los de la
 * columna atencion_alerta.resultado_visita.
 */
public enum ResultadoVisita {

	/** Está bien: falta decir si el evento ocurrió. */
	BIEN,

	/** Requiere apoyo no urgente: la alerta se cierra como POSITIVA y queda en seguimiento. */
	APOYO_NO_URGENTE,

	/** Es una emergencia: falta su desenlace (trasladada, atendida en casa u otro). */
	EMERGENCIA

}
