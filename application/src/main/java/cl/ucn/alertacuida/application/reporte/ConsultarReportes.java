package cl.ucn.alertacuida.application.reporte;

import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.reporte.modelo.IndicadoresPeriodo;
import cl.ucn.alertacuida.application.reporte.modelo.Periodo;
import cl.ucn.alertacuida.application.reporte.modelo.ReporteUsuario;
import cl.ucn.alertacuida.application.reporte.puerto.Reportes;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Reportes diarios y semanales por usuario, para el seguimiento preventivo. Se calculan al pedirlos, sin tabla
 * ni tarea programada (decisión del 19-11-2026, como dice el modelo de datos). El semanal va de lunes a domingo
 * y trae la semana anterior para comparar.
 */
public class ConsultarReportes {

	private final Reportes reportes;
	private final Clock reloj;

	/** @param reloj da la fecha de hoy en Chile */
	public ConsultarReportes(Reportes reportes, Clock reloj) {
		this.reportes = reportes;
		this.reloj = reloj;
	}

	/**
	 * @param dia un día de Chile; si falta, hoy
	 * @throws NoEncontrado si el usuario no existe
	 */
	public ReporteUsuario diario(UUID idPaciente, LocalDate dia) {
		verificar(idPaciente);
		LocalDate fecha = dia != null ? dia : hoy();
		return new ReporteUsuario(Periodo.DIARIO, fecha, fecha, indicadores(idPaciente, fecha, fecha), null);
	}

	/**
	 * @param dia cualquier día de la semana pedida; si falta, hoy
	 * @throws NoEncontrado si el usuario no existe
	 */
	public ReporteUsuario semanal(UUID idPaciente, LocalDate dia) {
		verificar(idPaciente);
		LocalDate lunes = (dia != null ? dia : hoy()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate domingo = lunes.plusDays(6);
		return new ReporteUsuario(Periodo.SEMANAL, lunes, domingo, indicadores(idPaciente, lunes, domingo),
				indicadores(idPaciente, lunes.minusWeeks(1), domingo.minusWeeks(1)));
	}

	/** @throws NoEncontrado si el usuario no existe */
	private void verificar(UUID idPaciente) {
		if (!reportes.existePaciente(idPaciente)) {
			throw new NoEncontrado("No existe ese usuario");
		}
	}

	/** @return los indicadores de esos días de Chile, ambos incluidos */
	private IndicadoresPeriodo indicadores(UUID idPaciente, LocalDate desde, LocalDate hasta) {
		return reportes.indicadores(idPaciente, inicioDelDia(desde), inicioDelDia(hasta.plusDays(1)));
	}

	/** @return hoy en Chile */
	private LocalDate hoy() {
		return LocalDate.now(reloj.withZone(ZonaHoraria.CHILE));
	}

	/** @return las 00:00 de ese día en Chile */
	private static Instant inicioDelDia(LocalDate dia) {
		return dia.atStartOfDay(ZonaHoraria.CHILE).toInstant();
	}

}
