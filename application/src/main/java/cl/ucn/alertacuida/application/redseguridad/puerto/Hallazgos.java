package cl.ucn.alertacuida.application.redseguridad.puerto;

import cl.ucn.alertacuida.application.redseguridad.modelo.CriterioHallazgos;
import cl.ucn.alertacuida.application.redseguridad.modelo.Hallazgo;
import java.util.List;

/** Busca en las lecturas guardadas los posibles eventos que el reloj no alertó. */
public interface Hallazgos {

	/**
	 * @return las lecturas del rango más allá del límite grave vigente al medir, con el módulo de esa variable
	 *         encendido y sin alerta del reloj de FC, SpO2, FC+SpO2 o tendencia dentro de la ventana (también
	 *         canceladas; sin simulacros); la más reciente primero
	 */
	List<Hallazgo> buscar(CriterioHallazgos criterio);

}
