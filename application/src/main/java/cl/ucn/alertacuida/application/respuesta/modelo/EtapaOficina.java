package cl.ucn.alertacuida.application.respuesta.modelo;

/**
 * En qué va la oficina PRLAC con una alerta, según lo que ve el coordinador del enlace. No se guarda: se calcula
 * con lo registrado en atencion_alerta.
 */
public enum EtapaOficina {

	/** Nadie la tomó: se ofrece "Tomo la alerta". */
	SIN_TOMAR,

	/** La tiene otra persona: solo se muestra quién. */
	A_CARGO_DE_OTRO,

	/** Tomada por este coordinador: llamar y decir cómo resultó (N1-02). */
	LLAMAR,

	/** La persona está bien: decir si el evento ocurrió para cerrar (N1-03). */
	CONFIRMAR_EVENTO,

	/** Necesita atención o no contesta: asignar personal o decir que no hay (N1-05). */
	COORDINAR_VISITA,

	/** No había personal: el reloj sigue avisando a los contactos (N1-06). */
	SIN_DISPONIBILIDAD,

	/** Personal asignado, esperando que llegue (N1-07 y, pasado el plazo, N1-08). */
	ESPERANDO_LLEGADA,

	/** El personal llegó: decir cómo encontró a la persona (N1-09). */
	RESULTADO_VISITA,

	/** Emergencia: decir su desenlace (N1-11). */
	EMERGENCIA,

	/** Cerrada o cancelada (N1-04, N1-10). */
	CERRADA

}
