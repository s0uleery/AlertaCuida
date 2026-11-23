package cl.ucn.alertacuida.application.tendencia.puerto;

import cl.ucn.alertacuida.application.tendencia.modelo.Agrupacion;
import cl.ucn.alertacuida.application.tendencia.modelo.MarcaAlerta;
import cl.ucn.alertacuida.application.tendencia.modelo.PuntoTendencia;
import cl.ucn.alertacuida.application.tendencia.modelo.UbicacionTendencia;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lecturas y alertas de un paciente, de todos sus relojes (también los reemplazados). */
public interface Tendencias {

	/** @return si el paciente existe */
	boolean existePaciente(UUID idPaciente);

	/** @return las lecturas de esa variable medidas en [desde, hasta), juntas según la agrupación, en orden */
	List<PuntoTendencia> puntos(UUID idPaciente, TipoLectura variable, Instant desde, Instant hasta,
			Agrupacion agrupacion);

	/** @return las alertas detectadas en [desde, hasta), sin simulacros, en orden de detección */
	List<MarcaAlerta> alertas(UUID idPaciente, Instant desde, Instant hasta);

	/**
	 * @return las posiciones GPS tomadas en [desde, hasta), en orden: todas con {@link Agrupacion#LECTURA}; la
	 *         última de cada hora o día de Chile con las demás
	 */
	List<UbicacionTendencia> ubicaciones(UUID idPaciente, Instant desde, Instant hasta, Agrupacion agrupacion);

}
