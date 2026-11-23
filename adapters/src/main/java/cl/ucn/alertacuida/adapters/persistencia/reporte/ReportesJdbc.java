package cl.ucn.alertacuida.adapters.persistencia.reporte;

import cl.ucn.alertacuida.application.reporte.modelo.Estadistica;
import cl.ucn.alertacuida.application.reporte.modelo.IndicadoresPeriodo;
import cl.ucn.alertacuida.application.reporte.modelo.UsoReloj;
import cl.ucn.alertacuida.application.reporte.puerto.Reportes;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Indicadores de los reportes, calculados con consultas sobre registro_vital, sincronizacion y alerta (el
 * modelo de datos no guarda reportes). Junta los datos de todos los relojes del paciente.
 */
@Repository
public class ReportesJdbc implements Reportes {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public ReportesJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean existePaciente(UUID idPaciente) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM paciente WHERE id_paciente = :p)")
				.param("p", idPaciente).query(Boolean.class).single();
	}

	@Override
	public IndicadoresPeriodo indicadores(UUID idPaciente, Instant desde, Instant hasta) {
		Map<TipoLectura, Estadistica> vitales = vitales(idPaciente, desde, hasta);
		Map<TipoEvento, Integer> porTipo = new EnumMap<>(TipoEvento.class);
		Map<Severidad, Integer> porGravedad = new EnumMap<>(Severidad.class);
		int canceladas = 0;
		for (AlertaContada alerta : alertas(idPaciente, desde, hasta)) {
			if (alerta.cancelada()) {
				canceladas++;
			} else {
				porTipo.merge(alerta.tipo(), 1, Integer::sum);
				porGravedad.merge(alerta.severidad(), 1, Integer::sum);
			}
		}
		UsoYBateria uso = uso(idPaciente, desde, hasta);
		return new IndicadoresPeriodo(vitales.getOrDefault(TipoLectura.FC, SIN_LECTURAS),
				vitales.getOrDefault(TipoLectura.SPO2, SIN_LECTURAS), uso.uso(), uso.bateriaMinima(), porTipo,
				porGravedad, canceladas);
	}

	/** Una variable sin lecturas en el período. */
	private static final Estadistica SIN_LECTURAS = new Estadistica(null, null, null, 0);

	/** @return promedio, mínimo, máximo y cantidad de cada variable que tuvo lecturas */
	private Map<TipoLectura, Estadistica> vitales(UUID idPaciente, Instant desde, Instant hasta) {
		Map<TipoLectura, Estadistica> vitales = new EnumMap<>(TipoLectura.class);
		jdbc.sql("""
				SELECT r.tipo, round(avg(r.valor), 1) AS promedio, min(r.valor) AS minimo, max(r.valor) AS maximo,
					count(*) AS lecturas
				FROM registro_vital r JOIN asignacion_reloj a USING (id_asignacion)
				WHERE a.id_paciente = :p AND r.fecha_hora_medicion >= :desde AND r.fecha_hora_medicion < :hasta
				GROUP BY r.tipo""")
				.param("p", idPaciente).param("desde", Timestamp.from(desde)).param("hasta", Timestamp.from(hasta))
				.query((fila, n) -> vitales.put(TipoLectura.valueOf(fila.getString("tipo")),
						new Estadistica(fila.getDouble("promedio"), fila.getDouble("minimo"), fila.getDouble("maximo"),
								fila.getInt("lecturas"))))
				.list();
		return vitales;
	}

	/** @return minutos puesto, sincronizaciones y batería mínima de los lotes recibidos en el período */
	private UsoYBateria uso(UUID idPaciente, Instant desde, Instant hasta) {
		return jdbc.sql("""
				SELECT COALESCE(sum(s.minutos_puesto), 0) AS minutos, count(*) AS lotes,
					min(s.nivel_bateria) AS bateria
				FROM sincronizacion s JOIN asignacion_reloj a USING (id_asignacion)
				WHERE a.id_paciente = :p AND s.fecha_hora_recepcion >= :desde AND s.fecha_hora_recepcion < :hasta""")
				.param("p", idPaciente).param("desde", Timestamp.from(desde)).param("hasta", Timestamp.from(hasta))
				.query((fila, n) -> new UsoYBateria(new UsoReloj(fila.getInt("minutos"), fila.getInt("lotes")),
						fila.getObject("bateria", Integer.class)))
				.single();
	}

	/**
	 * Las alertas no simuladas detectadas en el período, con su gravedad actual. Las del servidor no tienen
	 * hora del reloj: se usa la de recepción.
	 */
	private List<AlertaContada> alertas(UUID idPaciente, Instant desde, Instant hasta) {
		return jdbc.sql("""
				SELECT al.tipo_evento, e.severidad_actual, al.fecha_hora_cancelacion IS NOT NULL AS cancelada
				FROM alerta al
				JOIN alerta_estado e USING (id_alerta)
				JOIN asignacion_reloj a USING (id_asignacion)
				WHERE a.id_paciente = :p AND NOT al.es_simulacro
					AND COALESCE(al.fecha_hora_deteccion, al.fecha_hora_recepcion) >= :desde
					AND COALESCE(al.fecha_hora_deteccion, al.fecha_hora_recepcion) < :hasta""")
				.param("p", idPaciente).param("desde", Timestamp.from(desde)).param("hasta", Timestamp.from(hasta))
				.query((fila, n) -> new AlertaContada(TipoEvento.valueOf(fila.getString("tipo_evento")),
						Severidad.valueOf(fila.getString("severidad_actual")), fila.getBoolean("cancelada")))
				.list();
	}

	/** Una alerta para contar. */
	private record AlertaContada(TipoEvento tipo, Severidad severidad, boolean cancelada) {
	}

	/** Lo que sale de las sincronizaciones. */
	private record UsoYBateria(UsoReloj uso, Integer bateriaMinima) {
	}

}
