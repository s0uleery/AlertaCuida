package cl.ucn.alertacuida.application.vigilancia;

import cl.ucn.alertacuida.application.vigilancia.modelo.LoteEstado;
import cl.ucn.alertacuida.application.vigilancia.modelo.RelojVigilado;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;

/** Una regla que detecta un problema técnico de un reloj a partir de sus sincronizaciones. */
public interface ReglaAusencia {

	/** @return el tipo de la alerta técnica que crea esta regla */
	TipoEvento tipo();

	/** @return la gravedad de esa alerta */
	Severidad severidad();

	/**
	 * @param reloj el reloj con sus últimas sincronizaciones
	 * @param ahora la hora de la revisión
	 * @return si el problema existe, si ya pasó o si no se puede saber
	 */
	EstadoRegla evaluar(RelojVigilado reloj, Instant ahora);

	/**
	 * @param lote una sincronización
	 * @return si esa sincronización demuestra que el reloj volvió a la normalidad
	 */
	boolean normaliza(LoteEstado lote);

	/**
	 * @param reloj el reloj con el problema
	 * @return la evidencia de la alerta, como texto JSON
	 */
	String detalle(RelojVigilado reloj);

}
