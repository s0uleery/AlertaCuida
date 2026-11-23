package cl.ucn.alertacuida.application.alerta.puerto;

import cl.ucn.alertacuida.application.alerta.modelo.DestinoDerivacion;
import cl.ucn.alertacuida.application.alerta.modelo.TipoSeguimiento;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Las acciones del equipo sobre un caso (tabla seguimiento) y el catálogo de destinos de derivación. */
public interface Seguimientos {

	/**
	 * Guarda la acción solo si la alerta no está cerrada del todo ni cancelada; la condición va en la misma
	 * escritura, para que no se cuele una acción justo al cerrar el seguimiento.
	 *
	 * @param destino solo en DERIVACION
	 * @param nota    en claro; se guarda cifrada. Puede ser null en DERIVACION
	 * @return el id de la acción, o vacío si la alerta ya está cerrada
	 */
	Optional<UUID> registrar(UUID idAlerta, UUID idUsuario, TipoSeguimiento tipo, String destino, String nota,
			Instant ahora);

	/** @return si ese destino existe y está activo */
	boolean destinoActivo(String codigo);

	/** @return los destinos activos, por nombre */
	List<DestinoDerivacion> destinosActivos();

}
