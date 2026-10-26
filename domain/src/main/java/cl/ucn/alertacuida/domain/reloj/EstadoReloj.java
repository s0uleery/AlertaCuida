package cl.ucn.alertacuida.domain.reloj;

/**
 * Estado físico de un reloj (smartwatch.estado). Si está asignado a un paciente no es un estado:
 * se sabe por su asignación vigente.
 */
public enum EstadoReloj {

	/** Funciona: es el único estado que se puede vincular a un paciente. */
	OPERATIVO,

	/** En revisión o reparación: no se puede vincular. */
	EN_REPARACION,

	/** Dado de baja del inventario: no vuelve a usarse, pero su historial se conserva. */
	RETIRADO;

	/** @return si un reloj en este estado se puede vincular a un paciente */
	public boolean vinculable() {
		return this == OPERATIVO;
	}

}
