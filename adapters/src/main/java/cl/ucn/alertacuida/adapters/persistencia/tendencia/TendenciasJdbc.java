package cl.ucn.alertacuida.adapters.persistencia.tendencia;

import cl.ucn.alertacuida.application.tendencia.modelo.Agrupacion;
import cl.ucn.alertacuida.application.tendencia.modelo.MarcaAlerta;
import cl.ucn.alertacuida.application.tendencia.modelo.PuntoTendencia;
import cl.ucn.alertacuida.application.tendencia.modelo.UbicacionTendencia;
import cl.ucn.alertacuida.application.tendencia.puerto.Tendencias;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lecturas y alertas de un paciente para los gráficos, de todos sus relojes: se une registro_vital y alerta con
 * asignacion_reloj por id_paciente, así que un reloj reemplazado sigue contando.
 */
@Repository
public class TendenciasJdbc implements Tendencias {

	/** Cada lectura tal cual: promedio, mínimo y máximo son su valor. */
	private static final String POR_LECTURA = """
			SELECT r.fecha_hora_medicion AS fecha, r.valor AS promedio, r.valor AS minimo, r.valor AS maximo,
				1 AS cantidad
			FROM registro_vital r JOIN asignacion_reloj a USING (id_asignacion)
			WHERE a.id_paciente = :paciente AND r.tipo = :tipo
				AND r.fecha_hora_medicion >= :desde AND r.fecha_hora_medicion < :hasta
			ORDER BY r.fecha_hora_medicion""";

	/** Juntas por hora o por día de Chile; la fecha es el comienzo de esa hora o de ese día. */
	private static final String AGRUPADAS = """
			SELECT date_trunc(CAST(:unidad AS text), r.fecha_hora_medicion AT TIME ZONE CAST(:zona AS text))
					AT TIME ZONE CAST(:zona AS text)
					AS fecha,
				round(avg(r.valor), 1) AS promedio, min(r.valor) AS minimo, max(r.valor) AS maximo,
				count(*) AS cantidad
			FROM registro_vital r JOIN asignacion_reloj a USING (id_asignacion)
			WHERE a.id_paciente = :paciente AND r.tipo = :tipo
				AND r.fecha_hora_medicion >= :desde AND r.fecha_hora_medicion < :hasta
			GROUP BY 1
			ORDER BY 1""";

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public TendenciasJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean existePaciente(UUID idPaciente) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM paciente WHERE id_paciente = :p)")
				.param("p", idPaciente).query(Boolean.class).single();
	}

	@Override
	public List<PuntoTendencia> puntos(UUID idPaciente, TipoLectura variable, Instant desde, Instant hasta,
			Agrupacion agrupacion) {
		var consulta = agrupacion == Agrupacion.LECTURA ? jdbc.sql(POR_LECTURA)
				: jdbc.sql(AGRUPADAS).param("unidad", agrupacion == Agrupacion.HORA ? "hour" : "day")
						.param("zona", ZonaHoraria.CHILE.getId());
		return consulta.param("paciente", idPaciente).param("tipo", variable.name())
				.param("desde", Timestamp.from(desde)).param("hasta", Timestamp.from(hasta))
				.query((fila, n) -> new PuntoTendencia(fila.getTimestamp("fecha").toInstant(),
						fila.getDouble("promedio"), fila.getDouble("minimo"), fila.getDouble("maximo"),
						fila.getInt("cantidad")))
				.list();
	}

	/** Las alertas del servidor no tienen hora del reloj: se usa la de recepción. */
	@Override
	public List<MarcaAlerta> alertas(UUID idPaciente, Instant desde, Instant hasta) {
		return jdbc.sql("""
				SELECT al.id_alerta, al.numero, al.tipo_evento, al.severidad, d.deteccion,
					al.fecha_hora_cancelacion IS NOT NULL AS cancelada
				FROM alerta al
				JOIN asignacion_reloj a USING (id_asignacion)
				CROSS JOIN LATERAL (
					SELECT COALESCE(al.fecha_hora_deteccion, al.fecha_hora_recepcion) AS deteccion) d
				WHERE a.id_paciente = :paciente AND NOT al.es_simulacro
					AND d.deteccion >= :desde AND d.deteccion < :hasta
				ORDER BY d.deteccion, al.numero""")
				.param("paciente", idPaciente)
				.param("desde", Timestamp.from(desde)).param("hasta", Timestamp.from(hasta))
				.query((fila, n) -> new MarcaAlerta(fila.getObject("id_alerta", UUID.class), fila.getLong("numero"),
						TipoEvento.valueOf(fila.getString("tipo_evento")),
						Severidad.valueOf(fila.getString("severidad")),
						fila.getTimestamp("deteccion").toInstant(), fila.getBoolean("cancelada")))
				.list();
	}

	/**
	 * Con {@link Agrupacion#LECTURA}, todas; con las demás, DISTINCT ON por la hora o el día de Chile deja la
	 * última de cada uno.
	 *
	 * <p>El período se calcula una sola vez (columna {@code p.periodo}): si la expresión con {@code :unidad} y
	 * {@code :zona} se repitiera en DISTINCT ON y en ORDER BY, cada aparición sería un parámetro distinto y
	 * PostgreSQL no las reconocería como la misma expresión.
	 */
	@Override
	public List<UbicacionTendencia> ubicaciones(UUID idPaciente, Instant desde, Instant hasta,
			Agrupacion agrupacion) {
		String periodo = agrupacion == Agrupacion.LECTURA ? "u.fecha_hora_medicion"
				: "date_trunc(CAST(:unidad AS text), u.fecha_hora_medicion AT TIME ZONE CAST(:zona AS text))";
		var consulta = jdbc.sql("""
				SELECT latitud, longitud, precision_metros, fecha_hora_medicion FROM (
					SELECT DISTINCT ON (p.periodo) u.latitud, u.longitud, u.precision_metros, u.fecha_hora_medicion
					FROM ubicacion u JOIN asignacion_reloj a USING (id_asignacion)
					CROSS JOIN LATERAL (SELECT %s AS periodo) p
					WHERE a.id_paciente = :paciente
						AND u.fecha_hora_medicion >= :desde AND u.fecha_hora_medicion < :hasta
					ORDER BY p.periodo, u.fecha_hora_medicion DESC) ultimas
				ORDER BY fecha_hora_medicion""".formatted(periodo));
		if (agrupacion != Agrupacion.LECTURA) {
			consulta = consulta.param("unidad", agrupacion == Agrupacion.HORA ? "hour" : "day")
					.param("zona", ZonaHoraria.CHILE.getId());
		}
		return consulta.param("paciente", idPaciente)
				.param("desde", Timestamp.from(desde)).param("hasta", Timestamp.from(hasta))
				.query((fila, n) -> new UbicacionTendencia(fila.getDouble("latitud"), fila.getDouble("longitud"),
						fila.getObject("precision_metros") == null ? null : fila.getDouble("precision_metros"),
						fila.getTimestamp("fecha_hora_medicion").toInstant()))
				.list();
	}
}
