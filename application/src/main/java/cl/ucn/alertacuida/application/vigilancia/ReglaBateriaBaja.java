package cl.ucn.alertacuida.application.vigilancia;

import cl.ucn.alertacuida.application.vigilancia.modelo.LoteEstado;
import cl.ucn.alertacuida.application.vigilancia.modelo.RelojVigilado;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.List;

/** Un reloj que informa batería baja en sincronizaciones seguidas: nadie lo puso a cargar. */
final class ReglaBateriaBaja implements ReglaAusencia {

	private final int minima;

	/** @param minima porcentaje desde el cual la batería ya no se considera baja */
	ReglaBateriaBaja(int minima) {
		this.minima = minima;
	}

	@Override
	public TipoEvento tipo() {
		return TipoEvento.BATERIA_BAJA;
	}

	@Override
	public Severidad severidad() {
		return Severidad.LEVE;
	}

	/** Un lote que no informó la batería no permite decidir nada. */
	@Override
	public EstadoRegla evaluar(RelojVigilado reloj, Instant ahora) {
		List<LoteEstado> lotes = reloj.ultimosLotes();
		if (lotes.isEmpty() || lotes.get(0).nivelBateria() == null) {
			return EstadoRegla.SIN_CAMBIO;
		}
		if (normaliza(lotes.get(0))) {
			return EstadoRegla.NORMAL;
		}
		boolean persiste = lotes.size() >= VigilarRelojes.LOTES_SEGUIDOS
				&& lotes.stream().limit(VigilarRelojes.LOTES_SEGUIDOS).allMatch(this::esBaja);
		return persiste ? EstadoRegla.ACTIVA : EstadoRegla.SIN_CAMBIO;
	}

	@Override
	public boolean normaliza(LoteEstado lote) {
		return lote.nivelBateria() != null && lote.nivelBateria() >= minima;
	}

	@Override
	public String detalle(RelojVigilado reloj) {
		return "{\"nivelBateria\": " + reloj.ultimosLotes().get(0).nivelBateria() + "}";
	}

	/** @return si ese lote informó una batería bajo el mínimo */
	private boolean esBaja(LoteEstado lote) {
		return lote.nivelBateria() != null && lote.nivelBateria() < minima;
	}

}
