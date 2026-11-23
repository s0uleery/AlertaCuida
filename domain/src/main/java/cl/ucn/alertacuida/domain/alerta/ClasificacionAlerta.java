package cl.ucn.alertacuida.domain.alerta;

/** Conclusión del equipo al cerrar una alerta de salud. Las alertas técnicas no se clasifican. */
public enum ClasificacionAlerta {
	/** Hubo un evento real: el caso queda en seguimiento. */
	POSITIVA,
	/** No pasó nada: el caso se cierra. */
	FALSA_ALARMA
}
