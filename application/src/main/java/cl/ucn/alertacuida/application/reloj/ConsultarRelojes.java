package cl.ucn.alertacuida.application.reloj;

import cl.ucn.alertacuida.application.reloj.modelo.RelojConPaciente;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import java.util.List;

/**
 * Inventario de relojes con el paciente que usa cada uno. El estado técnico (batería, versión) se
 * agregará más adelante.
 */
public class ConsultarRelojes {

	private final Relojes relojes;

	/** Recibe el puerto del inventario; lo arma ConfiguracionPacientesYRelojes. */
	public ConsultarRelojes(Relojes relojes) {
		this.relojes = relojes;
	}

	/** @return todos los relojes, en cualquier estado, ordenados por identificador */
	public List<RelojConPaciente> listar() {
		return relojes.listar();
	}

}
