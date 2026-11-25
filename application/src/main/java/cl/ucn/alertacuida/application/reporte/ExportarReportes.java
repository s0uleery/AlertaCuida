package cl.ucn.alertacuida.application.reporte;

import cl.ucn.alertacuida.application.alerta.ConsultarAlertas;
import cl.ucn.alertacuida.application.alerta.modelo.ResumenAlerta;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.paciente.modelo.Paciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.application.reporte.modelo.DocumentoExportable;
import cl.ucn.alertacuida.application.reporte.modelo.DocumentoExportable.Seccion;
import cl.ucn.alertacuida.application.reporte.modelo.Estadistica;
import cl.ucn.alertacuida.application.reporte.modelo.IndicadoresPeriodo;
import cl.ucn.alertacuida.application.reporte.modelo.Periodo;
import cl.ucn.alertacuida.application.reporte.modelo.ReporteUsuario;
import cl.ucn.alertacuida.domain.alerta.EstadoAtencion;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Arma los reportes que se exportan a Excel o PDF desde Reportería (decisión del 25-11-2026): el reporte diario o
 * semanal de un usuario y la gestión operativa del equipo en un período. Llevan lo mismo que muestra la pantalla,
 * nada más, y cada exportación queda en la auditoría como EXPORTAR REPORTE.
 */
public class ExportarReportes {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
	private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
	/** Un período más largo no se exporta: la lista de alertas del dashboard trae las 200 más recientes. */
	private static final int MAXIMO_DIAS = 93;

	private final ConsultarReportes reportes;
	private final ConsultarAlertas alertas;
	private final Pacientes pacientes;
	private final RegistroAuditoria auditoria;
	private final Clock reloj;

	/** Recibe lo que usa; lo arma ConfiguracionReportes. */
	public ExportarReportes(ConsultarReportes reportes, ConsultarAlertas alertas, Pacientes pacientes,
			RegistroAuditoria auditoria, Clock reloj) {
		this.reportes = reportes;
		this.alertas = alertas;
		this.pacientes = pacientes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/**
	 * @param idPaciente el usuario
	 * @param periodo    diario o semanal
	 * @param dia        el día pedido (cualquiera de la semana, en el semanal); null = hoy
	 * @param idUsuario  quien exporta, para la auditoría
	 * @param ip         desde dónde
	 * @return el reporte del usuario, listo para dibujar
	 * @throws NoEncontrado si el usuario no existe
	 */
	public DocumentoExportable usuario(UUID idPaciente, Periodo periodo, LocalDate dia, UUID idUsuario, String ip) {
		Paciente paciente = pacientes.buscar(idPaciente).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
		ReporteUsuario reporte = periodo == Periodo.DIARIO ? reportes.diario(idPaciente, dia)
				: reportes.semanal(idPaciente, dia);
		String nombre = paciente.datos().nombres() + " " + paciente.datos().apellidos();
		String rango = periodo == Periodo.DIARIO ? FECHA.format(reporte.desde())
				: FECHA.format(reporte.desde()) + " al " + FECHA.format(reporte.hasta());
		List<Seccion> secciones = new ArrayList<>();
		secciones.add(indicadores(reporte));
		secciones.add(alertasDelPeriodo(reporte.indicadores()));
		registrar(idUsuario, idPaciente.toString(), ip);
		return new DocumentoExportable("Reporte " + (periodo == Periodo.DIARIO ? "diario" : "semanal") + " · " + nombre,
				rango + " · emitido el " + ahora(), secciones);
	}

	/**
	 * @param desde     primer día (incluido)
	 * @param hasta     último día (incluido)
	 * @param idUsuario quien exporta
	 * @param ip        desde dónde
	 * @return la gestión operativa del período: totales, gravedad, estado, carga por profesional y las alertas
	 * @throws SolicitudRechazada si el período está al revés o es muy largo
	 */
	public DocumentoExportable gestion(LocalDate desde, LocalDate hasta, UUID idUsuario, String ip) {
		if (hasta.isBefore(desde)) {
			throw new SolicitudRechazada("El período termina antes de empezar");
		}
		if (desde.plusDays(MAXIMO_DIAS).isBefore(hasta)) {
			throw new SolicitudRechazada("El período no puede ser de más de " + MAXIMO_DIAS + " días");
		}
		List<ResumenAlerta> delPeriodo = alertas.listar().stream()
				.filter(alerta -> !alerta.simulacro())
				.filter(alerta -> {
					LocalDate dia = diaEnChile(alerta.recepcion());
					return !dia.isBefore(desde) && !dia.isAfter(hasta);
				})
				.toList();
		List<Seccion> secciones = List.of(
				new Seccion("Resumen", List.of("Indicador", "Valor"), List.of(
						List.of("Alertas totales", String.valueOf(delPeriodo.size())),
						List.of("Sin asignar", String.valueOf(contar(delPeriodo, EstadoAtencion.SIN_ASIGNAR))),
						List.of("En atención", String.valueOf(contar(delPeriodo, EstadoAtencion.EN_ATENCION))),
						List.of("En seguimiento", String.valueOf(contar(delPeriodo, EstadoAtencion.EN_SEGUIMIENTO))),
						List.of("Cerradas", String.valueOf(contar(delPeriodo, EstadoAtencion.RESUELTA))))),
				conteo("Distribución por gravedad", "Gravedad", delPeriodo.stream()
						.collect(Collectors.groupingBy(a -> nombre(a.severidad()), TreeMap::new,
								Collectors.counting()))),
				conteo("Carga por profesional", "Profesional", delPeriodo.stream()
						.collect(Collectors.groupingBy(a -> a.responsable() == null ? "Por asignar" : a.responsable(),
								TreeMap::new, Collectors.counting()))),
				new Seccion("Alertas del período",
						List.of("Folio", "Recibida", "Usuario", "Evento", "Gravedad", "Estado", "Encargado/a"),
						delPeriodo.stream().map(ExportarReportes::fila).toList()));
		registrar(idUsuario, "gestion", ip);
		return new DocumentoExportable("Gestión operativa de alertas",
				FECHA.format(desde) + " al " + FECHA.format(hasta) + " · emitido el " + ahora(), secciones);
	}

	/** @return la tabla de indicadores del usuario, con la semana anterior si es semanal */
	private static Seccion indicadores(ReporteUsuario reporte) {
		IndicadoresPeriodo actual = reporte.indicadores();
		IndicadoresPeriodo anterior = reporte.anterior();
		List<String> columnas = anterior == null ? List.of("Indicador", "Valor")
				: List.of("Indicador", "Este período", "Período anterior");
		List<List<String>> filas = new ArrayList<>();
		filas.add(fila("FC promedio (lpm)", estadistica(actual.fc()),
				anterior == null ? null : estadistica(anterior.fc())));
		filas.add(fila("SpO2 promedio (%)", estadistica(actual.spo2()),
				anterior == null ? null : estadistica(anterior.spo2())));
		filas.add(fila("Horas con el reloj puesto", horas(actual.uso().minutosPuesto()),
				anterior == null ? null : horas(anterior.uso().minutosPuesto())));
		filas.add(fila("Batería mínima (%)", texto(actual.bateriaMinima()),
				anterior == null ? null : texto(anterior.bateriaMinima())));
		filas.add(fila("Alertas", String.valueOf(total(actual)),
				anterior == null ? null : String.valueOf(total(anterior))));
		filas.add(fila("Alertas canceladas por el usuario", String.valueOf(actual.alertasCanceladas()),
				anterior == null ? null : String.valueOf(anterior.alertasCanceladas())));
		return new Seccion("Indicadores", columnas, filas);
	}

	/** @return las alertas del período por tipo y por gravedad */
	private static Seccion alertasDelPeriodo(IndicadoresPeriodo indicadores) {
		List<List<String>> filas = new ArrayList<>();
		indicadores.alertasPorTipo().forEach((tipo, n) -> filas.add(List.of("Tipo", nombre(tipo), String.valueOf(n))));
		indicadores.alertasPorGravedad().forEach((gravedad, n) ->
				filas.add(List.of("Gravedad", nombre(gravedad), String.valueOf(n))));
		return new Seccion("Alertas del período", List.of("Agrupación", "Valor", "Cantidad"), filas);
	}

	/** @return una fila de indicador, con o sin la columna del período anterior */
	private static List<String> fila(String indicador, String actual, String anterior) {
		return anterior == null ? List.of(indicador, actual) : List.of(indicador, actual, anterior);
	}

	/** @return la fila de una alerta en la gestión operativa */
	private static List<String> fila(ResumenAlerta alerta) {
		return List.of("#" + alerta.numero(), FECHA_HORA.format(alerta.recepcion().atZone(ZonaHoraria.CHILE)),
				alerta.paciente().nombres() + " " + alerta.paciente().apellidos(), nombre(alerta.tipoEvento()),
				nombre(alerta.severidad()), nombre(alerta.estadoAtencion()),
				alerta.responsable() == null ? "Por asignar" : alerta.responsable());
	}

	/** @return una tabla de dos columnas con un conteo */
	private static Seccion conteo(String titulo, String columna, Map<String, Long> conteo) {
		Map<String, Long> ordenado = new LinkedHashMap<>(conteo);
		return new Seccion(titulo, List.of(columna, "Alertas"),
				ordenado.entrySet().stream().map(e -> List.of(e.getKey(), String.valueOf(e.getValue()))).toList());
	}

	/** @return cuántas alertas están en ese estado */
	private static String contar(List<ResumenAlerta> alertas, EstadoAtencion estado) {
		return String.valueOf(alertas.stream().filter(a -> a.estadoAtencion() == estado).count());
	}

	/** @return el total de alertas del período */
	private static int total(IndicadoresPeriodo indicadores) {
		return indicadores.alertasPorGravedad().values().stream().mapToInt(Integer::intValue).sum();
	}

	/** @return "promedio (mín–máx)", o "Sin lecturas" */
	private static String estadistica(Estadistica e) {
		if (e == null || e.promedio() == null) {
			return "Sin lecturas";
		}
		return String.format(Locale.ROOT, "%.0f (%.0f–%.0f)", e.promedio(), e.minimo(), e.maximo());
	}

	/** @return los minutos como horas con un decimal */
	private static String horas(int minutos) {
		return String.format(Locale.ROOT, "%.1f", minutos / 60.0);
	}

	/** @return el número, o "—" si falta */
	private static String texto(Integer valor) {
		return valor == null ? "—" : String.valueOf(valor);
	}

	/** @return el nombre legible de una gravedad, un tipo de evento o un estado */
	private static String nombre(Enum<?> valor) {
		String texto = valor.name().replace('_', ' ').toLowerCase(Locale.ROOT);
		return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}

	/** @return el día en Chile de ese instante */
	private static LocalDate diaEnChile(Instant instante) {
		return instante.atZone(ZonaHoraria.CHILE).toLocalDate();
	}

	/** @return ahora, en Chile, legible */
	private String ahora() {
		return FECHA_HORA.format(reloj.instant().atZone(ZonaHoraria.CHILE));
	}

	/** Deja la exportación en la auditoría. */
	private void registrar(UUID idUsuario, String idEntidad, String ip) {
		auditoria.registrar(idUsuario, "EXPORTAR", "REPORTE", idEntidad, ip, reloj.instant());
	}

}
