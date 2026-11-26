package cl.ucn.alertacuida.application.respuesta.modelo;

/**
 * Lo que indica un contacto del paciente en la página de su enlace después de "Llamar al paciente"
 * (decisión del 26-11-2026). Recién entonces elige si va o no. Los nombres son los de la columna
 * registro_intento.resultado_llamada.
 */
public enum ResultadoLlamadaContacto {

	/** El paciente contestó la llamada. */
	CONTESTA,

	/** El paciente no contestó. */
	NO_CONTESTA

}
