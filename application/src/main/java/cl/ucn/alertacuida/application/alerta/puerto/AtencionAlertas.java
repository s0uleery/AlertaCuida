package cl.ucn.alertacuida.application.alerta.puerto;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaAtendible;
import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que hace el equipo con una alerta (tabla atencion_alerta). Cada escritura es condicional en la base,
 * para que de dos personas a la vez solo una lo logre.
 */
public interface AtencionAlertas {

	/** @return la alerta, si existe */
	Optional<AlertaAtendible> buscar(UUID idAlerta);

	/** @return si esa persona quedó a cargo (false si otra ya la tomó o ya está cerrada) */
	boolean asumir(UUID idAlerta, UUID idUsuario, Instant ahora);

	/**
	 * Cierra la alerta. Si nadie la había asumido, quien resuelve queda como responsable.
	 *
	 * @param clasificacion null en las alertas técnicas
	 * @return si la cerró (false si ya estaba cerrada)
	 */
	boolean resolver(UUID idAlerta, UUID idUsuario, ClasificacionAlerta clasificacion, String observaciones,
			Instant ahora);

	/** @return si cerró el seguimiento (false si la alerta no tiene uno abierto) */
	boolean cerrarSeguimiento(UUID idAlerta, Instant ahora);

	/** Deja registrado, sin fecha de envío, el aviso al reloj de que la alerta se resolvió. */
	void dejarAvisoResueltaPendiente(UUID idAlerta);

}
