package cl.ucn.alertacuida.application.avisoreloj;

import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.avisoreloj.puerto.BandejaAvisosReloj;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import java.time.Instant;
import java.util.UUID;

/** El reloj confirma que le llegó un aviso de una alerta; con eso el servidor deja de reintentarlo. */
public class AcusarAvisoReloj {

	private final BandejaAvisosReloj bandeja;

	/** @param bandeja avisos enviados al reloj */
	public AcusarAvisoReloj(BandejaAvisosReloj bandeja) {
		this.bandeja = bandeja;
	}

	/**
	 * Registra la recepción. Repetirlo no cambia la hora ya registrada.
	 *
	 * @param quien el reloj que lo informa, según su token
	 * @param fecha cuándo le llegó
	 * @throws NoEncontrado       si no hay un aviso con ese id para este reloj
	 * @throws SolicitudRechazada si falta la hora
	 */
	public void acusar(RelojAutenticado quien, UUID idAviso, Instant fecha) {
		if (fecha == null) {
			throw new SolicitudRechazada("Falta fechaHoraRecepcion");
		}
		if (!bandeja.esDelVinculo(idAviso, quien.idAsignacion())) {
			throw new NoEncontrado("No hay un aviso con ese id para este reloj");
		}
		bandeja.registrarRecepcion(idAviso, fecha);
	}

}
