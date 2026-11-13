package cl.ucn.alertacuida.application.vigilancia;

import cl.ucn.alertacuida.application.vigilancia.modelo.LoteEstado;
import cl.ucn.alertacuida.application.vigilancia.modelo.RelojVigilado;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Un reloj que sigue sin ponerse durante el día. De noche es normal sacárselo para cargarlo, así que
 * solo cuentan las sincronizaciones de un mismo día, dentro del horario; las de la noche no crean
 * alertas, y tampoco cierran una que siga abierta.
 */
final class ReglaRelojNoPuesto implements ReglaAusencia {

	private final int horaInicioDia;
	private final int horaFinDia;

	/**
	 * @param horaInicioDia hora de Chile (incluida) en que parte el horario
	 * @param horaFinDia    hora de Chile (excluida) en que termina
	 */
	ReglaRelojNoPuesto(int horaInicioDia, int horaFinDia) {
		this.horaInicioDia = horaInicioDia;
		this.horaFinDia = horaFinDia;
	}

	@Override
	public TipoEvento tipo() {
		return TipoEvento.RELOJ_NO_PUESTO;
	}

	@Override
	public Severidad severidad() {
		return Severidad.LEVE;
	}

	/** Un lote que no informó si estaba puesto no permite decidir nada. */
	@Override
	public EstadoRegla evaluar(RelojVigilado reloj, Instant ahora) {
		List<LoteEstado> lotes = reloj.ultimosLotes();
		if (lotes.isEmpty() || lotes.get(0).relojPuesto() == null) {
			return EstadoRegla.SIN_CAMBIO;
		}
		if (normaliza(lotes.get(0))) {
			return EstadoRegla.NORMAL;
		}
		return persisteDeDia(lotes) ? EstadoRegla.ACTIVA : EstadoRegla.SIN_CAMBIO;
	}

	@Override
	public boolean normaliza(LoteEstado lote) {
		return Boolean.TRUE.equals(lote.relojPuesto());
	}

	@Override
	public String detalle(RelojVigilado reloj) {
		return "{\"desde\": \"" + reloj.ultimosLotes().get(VigilarRelojes.LOTES_SEGUIDOS - 1).recepcion() + "\"}";
	}

	/** @return si los últimos lotes informan que no está puesto, todos del mismo día y en horario */
	private boolean persisteDeDia(List<LoteEstado> lotes) {
		if (lotes.size() < VigilarRelojes.LOTES_SEGUIDOS) {
			return false;
		}
		List<LoteEstado> ultimos = lotes.subList(0, VigilarRelojes.LOTES_SEGUIDOS);
		var fecha = horaChile(ultimos.get(0)).toLocalDate();
		return ultimos.stream().allMatch(lote -> Boolean.FALSE.equals(lote.relojPuesto()) && enHorario(lote)
				&& horaChile(lote).toLocalDate().equals(fecha));
	}

	/** @return si ese lote llegó dentro del horario */
	private boolean enHorario(LoteEstado lote) {
		int hora = horaChile(lote).getHour();
		return hora >= horaInicioDia && hora < horaFinDia;
	}

	/** @return la hora de recepción de ese lote, en Chile */
	private static ZonedDateTime horaChile(LoteEstado lote) {
		return lote.recepcion().atZone(ZonaHoraria.CHILE);
	}

}
