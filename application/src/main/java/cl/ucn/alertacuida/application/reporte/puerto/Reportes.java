package cl.ucn.alertacuida.application.reporte.puerto;

import cl.ucn.alertacuida.application.reporte.modelo.IndicadoresPeriodo;
import java.time.Instant;
import java.util.UUID;

/** Calcula los indicadores de un usuario, con los datos de todos sus relojes. */
public interface Reportes {

	/** @return si el paciente existe */
	boolean existePaciente(UUID idPaciente);

	/**
	 * @return los indicadores de [desde, hasta): lecturas por hora de medición, sincronizaciones por hora de
	 *         recepción y alertas por hora de detección
	 */
	IndicadoresPeriodo indicadores(UUID idPaciente, Instant desde, Instant hasta);

}
