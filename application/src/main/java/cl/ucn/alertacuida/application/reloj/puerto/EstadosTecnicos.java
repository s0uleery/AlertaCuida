package cl.ucn.alertacuida.application.reloj.puerto;

import cl.ucn.alertacuida.application.reloj.modelo.DatosTecnicosReloj;
import java.util.List;

/** Lee lo que hace falta para el estado técnico de los relojes. */
public interface EstadosTecnicos {

	/** @return los datos técnicos de todos los relojes registrados */
	List<DatosTecnicosReloj> todos();

}
