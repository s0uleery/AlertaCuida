package cl.ucn.alertacuida.application.tendencia;

import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.tendencia.modelo.Agrupacion;
import cl.ucn.alertacuida.application.tendencia.modelo.TendenciasPaciente;
import cl.ucn.alertacuida.application.tendencia.puerto.Tendencias;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Evolución de FC y SpO2 de un paciente para los gráficos, con la hora real de cada medición y sus alertas.
 * Cuanto más largo el rango, más se juntan las lecturas (decisión del 19-11-2026).
 */
public class ConsultarTendencias {

	/** Días del rango cuando no se indica. */
	static final int DIAS_POR_DEFECTO = 7;
	/** Hasta estos días, cada lectura. */
	static final int DIAS_POR_LECTURA = 2;
	/** Hasta estos días, por hora; más, por día. */
	static final int DIAS_POR_HORA = 31;
	/** Días máximos del rango. */
	static final int DIAS_MAXIMOS = 90;

	private final Tendencias tendencias;
	private final Clock reloj;

	/** @param reloj da la fecha de hoy en Chile */
	public ConsultarTendencias(Tendencias tendencias, Clock reloj) {
		this.tendencias = tendencias;
		this.reloj = reloj;
	}

	/**
	 * @param desde primer día de Chile, incluido; si falta, 6 días antes de hasta
	 * @param hasta último día de Chile, incluido; si falta, hoy
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si desde es posterior a hasta o el rango pasa de 90 días
	 */
	public TendenciasPaciente consultar(UUID idPaciente, LocalDate desde, LocalDate hasta) {
		LocalDate fin = hasta != null ? hasta : LocalDate.now(reloj.withZone(ZonaHoraria.CHILE));
		LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1);
		if (inicio.isAfter(fin)) {
			throw new SolicitudRechazada("La fecha de inicio es posterior a la de término");
		}
		long dias = ChronoUnit.DAYS.between(inicio, fin) + 1;
		if (dias > DIAS_MAXIMOS) {
			throw new SolicitudRechazada("El rango no puede pasar de " + DIAS_MAXIMOS + " días");
		}
		if (!tendencias.existePaciente(idPaciente)) {
			throw new NoEncontrado("No existe ese usuario");
		}
		Agrupacion agrupacion = dias <= DIAS_POR_LECTURA ? Agrupacion.LECTURA
				: dias <= DIAS_POR_HORA ? Agrupacion.HORA : Agrupacion.DIA;
		Instant comienzo = inicio.atStartOfDay(ZonaHoraria.CHILE).toInstant();
		Instant termino = fin.plusDays(1).atStartOfDay(ZonaHoraria.CHILE).toInstant();
		return new TendenciasPaciente(agrupacion, comienzo, termino,
				tendencias.puntos(idPaciente, TipoLectura.FC, comienzo, termino, agrupacion),
				tendencias.puntos(idPaciente, TipoLectura.SPO2, comienzo, termino, agrupacion),
				tendencias.alertas(idPaciente, comienzo, termino),
				tendencias.ubicaciones(idPaciente, comienzo, termino, agrupacion));
	}

}
