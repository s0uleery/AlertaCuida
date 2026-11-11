package cl.ucn.alertacuida.application.vigilancia;

import cl.ucn.alertacuida.application.vigilancia.modelo.LoteEstado;
import cl.ucn.alertacuida.application.vigilancia.modelo.RelojVigilado;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Duration;
import java.time.Instant;

/**
 * Un reloj que lleva demasiado tiempo sin sincronizar: sin señal, apagado o con el plan vencido. El
 * tiempo se cuenta desde la última sincronización o, si nunca sincronizó, desde que se vinculó.
 */
final class ReglaSilencioDatos implements ReglaAusencia {

	private final Duration silencio;

	/** @param silencio tiempo sin sincronizar desde el cual hay silencio de datos */
	ReglaSilencioDatos(Duration silencio) {
		this.silencio = silencio;
	}

	@Override
	public TipoEvento tipo() {
		return TipoEvento.SILENCIO_DATOS;
	}

	/** Media: mientras dure, el paciente queda sin protección. */
	@Override
	public Severidad severidad() {
		return Severidad.MEDIA;
	}

	@Override
	public EstadoRegla evaluar(RelojVigilado reloj, Instant ahora) {
		Instant ultimaSenal = reloj.ultimosLotes().isEmpty() ? reloj.inicioVinculo()
				: reloj.ultimosLotes().get(0).recepcion();
		return Duration.between(ultimaSenal, ahora).compareTo(silencio) >= 0 ? EstadoRegla.ACTIVA : EstadoRegla.NORMAL;
	}

	/** Cualquier sincronización demuestra que el reloj volvió a comunicarse. */
	@Override
	public boolean normaliza(LoteEstado lote) {
		return true;
	}

	@Override
	public String detalle(RelojVigilado reloj) {
		return reloj.ultimosLotes().isEmpty() ? "{\"ultimaSincronizacion\": null}"
				: "{\"ultimaSincronizacion\": \"" + reloj.ultimosLotes().get(0).recepcion() + "\"}";
	}

}
