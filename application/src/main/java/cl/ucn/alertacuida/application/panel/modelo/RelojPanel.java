package cl.ucn.alertacuida.application.panel.modelo;

import cl.ucn.alertacuida.domain.reloj.ColorSemaforo;
import cl.ucn.alertacuida.domain.reloj.MotivoSemaforo;
import cl.ucn.alertacuida.domain.telemetria.TipoConexion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El reloj vigente de un usuario en la portada, con su semáforo (el mismo de la lista de relojes).
 *
 * @param ultimaSincronizacion null si nunca sincronizó
 */
public record RelojPanel(UUID idDispositivo, String identificador, ColorSemaforo semaforo,
		List<MotivoSemaforo> motivos, Instant ultimaSincronizacion, Integer nivelBateria, TipoConexion tipoConexion,
		boolean configuracionPendiente) {

	/** Copia la lista para que nadie la cambie después. */
	public RelojPanel {
		motivos = List.copyOf(motivos);
	}

}
