package cl.ucn.alertacuida.application.vigilancia.modelo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un vínculo reloj-paciente vigente y sus últimas sincronizaciones.
 *
 * @param idAsignacion el vínculo
 * @param inicioVinculo desde cuándo está vinculado; un reloj que nunca sincronizó se mide desde aquí
 * @param ultimosLotes  sus últimas sincronizaciones, la más reciente primero; vacía si nunca sincronizó
 */
public record RelojVigilado(UUID idAsignacion, Instant inicioVinculo, List<LoteEstado> ultimosLotes) {

	/** Copia la lista para que nadie la cambie después. */
	public RelojVigilado {
		ultimosLotes = List.copyOf(ultimosLotes);
	}

}
