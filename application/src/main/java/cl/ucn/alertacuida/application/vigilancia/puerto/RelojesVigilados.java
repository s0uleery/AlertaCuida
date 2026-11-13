package cl.ucn.alertacuida.application.vigilancia.puerto;

import cl.ucn.alertacuida.application.vigilancia.modelo.LoteEstado;
import cl.ucn.alertacuida.application.vigilancia.modelo.RelojVigilado;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consulta de los relojes que hay que vigilar. */
public interface RelojesVigilados {

	/**
	 * @param lotes cuántas sincronizaciones traer por reloj, como máximo
	 * @return los vínculos vigentes con sus últimas sincronizaciones
	 */
	List<RelojVigilado> vigentes(int lotes);

	/**
	 * @param idAsignacion el vínculo
	 * @param desde        hora de recepción desde la que se buscan, sin incluirla
	 * @return las sincronizaciones de ese vínculo recibidas después de esa hora
	 */
	List<LoteEstado> lotesDesde(UUID idAsignacion, Instant desde);

}
