package cl.ucn.alertacuida.application.alerta.puerto;

import cl.ucn.alertacuida.application.alerta.modelo.FilaAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.HistorialAlerta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lectura de las alertas para el dashboard. */
public interface ConsultaAlertas {

	/**
	 * @param limite cuántas filas como máximo
	 * @return las alertas no canceladas: primero las que quedaron sin respuesta, después las más recientes
	 */
	List<FilaAlerta> listar(int limite);

	/** @return todo lo registrado de esa alerta, si existe */
	Optional<HistorialAlerta> historial(UUID idAlerta);

}
