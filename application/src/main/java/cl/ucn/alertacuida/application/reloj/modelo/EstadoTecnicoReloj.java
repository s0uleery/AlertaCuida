package cl.ucn.alertacuida.application.reloj.modelo;

import cl.ucn.alertacuida.domain.reloj.ColorSemaforo;
import cl.ucn.alertacuida.domain.reloj.MotivoSemaforo;
import cl.ucn.alertacuida.domain.telemetria.TipoConexion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Estado técnico de un reloj para el dashboard (inventario y ficha del usuario): su semáforo y lo que informó
 * en su última sincronización.
 *
 * @param semaforo               color del semáforo
 * @param motivos                por qué no está verde; vacío si lo está
 * @param ultimaSincronizacion   null si no ha sincronizado en su vínculo vigente
 * @param configuracionPendiente si todavía no aplica la configuración vigente del paciente
 */
public record EstadoTecnicoReloj(UUID idDispositivo, ColorSemaforo semaforo, List<MotivoSemaforo> motivos,
		Instant ultimaSincronizacion, Integer nivelBateria, Boolean relojPuesto, TipoConexion tipoConexion,
		String versionApp, boolean configuracionPendiente) {

	/** Copia la lista para que nadie la cambie después. */
	public EstadoTecnicoReloj {
		motivos = List.copyOf(motivos);
	}

}
