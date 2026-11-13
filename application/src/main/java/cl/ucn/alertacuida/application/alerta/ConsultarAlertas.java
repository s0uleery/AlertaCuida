package cl.ucn.alertacuida.application.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.CierreAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.DetalleAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.FilaAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.HistorialAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.PacienteDeAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.ResumenAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.ConsultaAlertas;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/** Lo que el dashboard muestra de las alertas: la lista y el detalle con su línea de tiempo. */
public class ConsultarAlertas {

	/** Tope de la lista, para que no crezca sin límite a medida que se acumulan alertas. */
	static final int MAXIMO_LISTA = 200;

	private final ConsultaAlertas consulta;
	private final PoliticaReintentos reintentosAvisoReloj;
	private final Clock reloj;

	/**
	 * @param reintentosAvisoReloj para saber qué avisos al reloj se agotaron sin acuse
	 * @param reloj                da la hora actual y la fecha de hoy, para calcular la edad
	 */
	public ConsultarAlertas(ConsultaAlertas consulta, PoliticaReintentos reintentosAvisoReloj, Clock reloj) {
		this.consulta = consulta;
		this.reintentosAvisoReloj = reintentosAvisoReloj;
		this.reloj = reloj;
	}

	/** @return las alertas no canceladas: primero las que quedaron sin respuesta, después las más recientes */
	public List<ResumenAlerta> listar() {
		LocalDate hoy = LocalDate.now(reloj.withZone(ZonaHoraria.CHILE));
		return consulta.listar(MAXIMO_LISTA).stream().map(fila -> resumen(fila, hoy)).toList();
	}

	/**
	 * @return la alerta con su línea de tiempo
	 * @throws NoEncontrado si no existe
	 */
	public DetalleAlerta detalle(UUID idAlerta) {
		HistorialAlerta historial = consulta.historial(idAlerta)
				.orElseThrow(() -> new NoEncontrado("No existe esa alerta"));
		LocalDate hoy = LocalDate.now(reloj.withZone(ZonaHoraria.CHILE));
		return DetalleAlerta.de(resumen(historial.alerta(), hoy), cierre(historial),
				LineaTiempoAlerta.de(historial, reintentosAvisoReloj, reloj.instant()));
	}

	/** @return la fila lista para mostrar, con la edad calculada */
	private static ResumenAlerta resumen(FilaAlerta fila, LocalDate hoy) {
		Integer edad = fila.nacimiento() == null ? null : Period.between(fila.nacimiento(), hoy).getYears();
		PacienteDeAlerta paciente = new PacienteDeAlerta(fila.idPaciente(), fila.nombres(), fila.apellidos(), edad);
		return new ResumenAlerta(fila.id(), fila.numero(), paciente, fila.tipoEvento(), fila.severidad(),
				fila.estadoContacto(), fila.estadoAtencion(), fila.responsable(), fila.recepcion(),
				fila.estadoContacto() == EstadoContacto.SIN_RESPUESTA);
	}

	/** @return cómo se cerró la alerta; null si sigue abierta */
	private static CierreAlerta cierre(HistorialAlerta historial) {
		return historial.cierre() == null ? null : new CierreAlerta(historial.cierre(), historial.resolutor(),
				historial.clasificacion(), historial.observaciones());
	}

}
