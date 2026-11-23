package cl.ucn.alertacuida.domain.alerta;

/**
 * De qué habla una alerta. Las de salud las enruta el reloj a los contactos del paciente; las técnicas hablan
 * del reloj y las avisa el servidor al equipo. En el dashboard van en pestañas separadas (Salud y Reloj).
 */
public enum CategoriaAlerta {
	SALUD,
	TECNICA;

	/** @return la categoría de ese tipo de evento */
	public static CategoriaAlerta de(TipoEvento tipo) {
		return tipo.esTecnica() ? TECNICA : SALUD;
	}

}
