package cl.ucn.alertacuida.application.calendario.puerto;

import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import java.util.Map;

/** El horario hábil guardado (tabla horario_habil): un tramo por cada día hábil. */
public interface HorariosHabiles {

	/** @return el tramo de cada día hábil; vacío si todavía no se guardó ninguno */
	Map<DiaSemana, TramoHabil> leer();

	/**
	 * Reemplaza el horario completo. Debe llamarse dentro de una transacción.
	 *
	 * @param horario los días que no aparecen dejan de ser hábiles
	 */
	void guardar(Map<DiaSemana, TramoHabil> horario);

}
