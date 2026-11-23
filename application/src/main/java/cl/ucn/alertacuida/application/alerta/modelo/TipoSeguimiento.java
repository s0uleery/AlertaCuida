package cl.ucn.alertacuida.application.alerta.modelo;

/** Qué hizo el equipo en un caso (seguimiento.tipo). */
public enum TipoSeguimiento {
	/** Llamó al usuario o a un contacto. */
	LLAMADA,
	/** Fue al domicilio. */
	VISITA,
	/** Derivó el caso a otra institución; exige el destino. */
	DERIVACION,
	/** Cualquier otra anotación. */
	NOTA
}
