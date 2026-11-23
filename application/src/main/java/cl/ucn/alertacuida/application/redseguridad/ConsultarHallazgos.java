package cl.ucn.alertacuida.application.redseguridad;

import cl.ucn.alertacuida.application.clinica.modelo.ValoresBase;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.redseguridad.modelo.CriterioHallazgos;
import cl.ucn.alertacuida.application.redseguridad.modelo.Hallazgo;
import cl.ucn.alertacuida.application.redseguridad.puerto.Hallazgos;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Red de seguridad del servidor: lista las lecturas más allá del límite grave que no tienen una alerta del
 * reloj cerca. Las alertas las decide el reloj; esto solo sirve para auditarlo, así que no guarda nada ni
 * avisa a nadie (decisión del 19-11-2026, versión mínima).
 */
public class ConsultarHallazgos {

	/** Una alerta del reloj a esta distancia de la lectura, o menos, la da por alertada. */
	static final Duration VENTANA = Duration.ofMinutes(30);
	/** Días del rango cuando no se indica. */
	static final int DIAS_POR_DEFECTO = 7;
	/** Días máximos del rango, para que la consulta no recorra meses de lecturas. */
	static final int DIAS_MAXIMOS = 31;
	/** Tope de la lista, como en la de alertas. */
	static final int MAXIMO_LISTA = 500;

	private final Hallazgos hallazgos;
	private final ValoresBase base;
	private final Clock reloj;

	/**
	 * @param base  umbrales que rigen cuando el paciente no tiene uno propio
	 * @param reloj da la fecha de hoy en Chile
	 */
	public ConsultarHallazgos(Hallazgos hallazgos, ValoresBase base, Clock reloj) {
		this.hallazgos = hallazgos;
		this.base = base;
		this.reloj = reloj;
	}

	/**
	 * @param desde primer día de Chile, incluido; si falta, 6 días antes de hasta
	 * @param hasta último día de Chile, incluido; si falta, hoy
	 * @return los hallazgos del rango, el más reciente primero
	 * @throws SolicitudRechazada si desde es posterior a hasta o el rango pasa de 31 días
	 */
	public List<Hallazgo> listar(LocalDate desde, LocalDate hasta) {
		LocalDate fin = hasta != null ? hasta : LocalDate.now(reloj.withZone(ZonaHoraria.CHILE));
		LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1);
		if (inicio.isAfter(fin)) {
			throw new SolicitudRechazada("La fecha de inicio es posterior a la de término");
		}
		if (ChronoUnit.DAYS.between(inicio, fin) + 1 > DIAS_MAXIMOS) {
			throw new SolicitudRechazada("El rango no puede pasar de " + DIAS_MAXIMOS + " días");
		}
		return hallazgos.buscar(new CriterioHallazgos(inicioDelDia(inicio), inicioDelDia(fin.plusDays(1)), VENTANA,
				base.umbral(TipoLectura.FC), base.umbral(TipoLectura.SPO2), MAXIMO_LISTA));
	}

	/** @return las 00:00 de ese día en Chile */
	private static Instant inicioDelDia(LocalDate dia) {
		return dia.atStartOfDay(ZonaHoraria.CHILE).toInstant();
	}

}
