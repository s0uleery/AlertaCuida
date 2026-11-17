package cl.ucn.alertacuida.adapters.persistencia.vigilancia;

import cl.ucn.alertacuida.application.vigilancia.modelo.LoteEstado;
import cl.ucn.alertacuida.application.vigilancia.modelo.RelojVigilado;
import cl.ucn.alertacuida.application.vigilancia.puerto.RelojesVigilados;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Vínculos vigentes (asignacion_reloj sin fecha_fin) y sus últimas filas de sincronizacion. */
@Repository
public class RelojesVigiladosJdbc implements RelojesVigilados {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public RelojesVigiladosJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** El LATERAL usa el índice (id_asignacion, fecha_hora_recepcion) de sincronizacion. */
	@Override
	public List<RelojVigilado> vigentes(int lotes) {
		Map<UUID, Acumulado> relojes = new LinkedHashMap<>();
		jdbc.sql("""
				SELECT v.id_asignacion, v.fecha_inicio, s.fecha_hora_recepcion, s.nivel_bateria, s.reloj_puesto
				FROM asignacion_reloj v
				LEFT JOIN LATERAL (
					SELECT l.fecha_hora_recepcion, l.nivel_bateria, l.reloj_puesto FROM sincronizacion l
					WHERE l.id_asignacion = v.id_asignacion
					ORDER BY l.fecha_hora_recepcion DESC LIMIT :lotes) s ON true
				WHERE v.fecha_fin IS NULL
				ORDER BY v.id_asignacion, s.fecha_hora_recepcion DESC""")
				.param("lotes", lotes)
				.query((RowCallbackHandler) fila -> agregar(relojes, fila));
		return relojes.entrySet().stream()
				.map(e -> new RelojVigilado(e.getKey(), e.getValue().inicio(), e.getValue().lotes()))
				.toList();
	}

	/** Usa el mismo índice (id_asignacion, fecha_hora_recepcion). */
	@Override
	public List<LoteEstado> lotesDesde(UUID idAsignacion, Instant desde) {
		return jdbc.sql("""
				SELECT fecha_hora_recepcion, nivel_bateria, reloj_puesto FROM sincronizacion
				WHERE id_asignacion = :asignacion AND fecha_hora_recepcion > :desde
				ORDER BY fecha_hora_recepcion DESC""")
				.param("asignacion", idAsignacion)
				.param("desde", Timestamp.from(desde))
				.query((fila, n) -> lote(fila, fila.getTimestamp("fecha_hora_recepcion")))
				.list();
	}

	/** @return el lote de esa fila, recibido a esa hora */
	private static LoteEstado lote(ResultSet fila, Timestamp recepcion) throws SQLException {
		return new LoteEstado(recepcion.toInstant(), fila.getObject("nivel_bateria", Integer.class),
				fila.getObject("reloj_puesto", Boolean.class));
	}

	/** Suma una fila al reloj que corresponde. Un reloj sin sincronizaciones trae una fila sin lote. */
	private static void agregar(Map<UUID, Acumulado> relojes, ResultSet fila) throws SQLException {
		UUID id = fila.getObject("id_asignacion", UUID.class);
		Instant inicio = fila.getTimestamp("fecha_inicio").toInstant();
		Acumulado reloj = relojes.computeIfAbsent(id, k -> new Acumulado(inicio, new ArrayList<>()));
		Timestamp recepcion = fila.getTimestamp("fecha_hora_recepcion");
		if (recepcion != null) {
			reloj.lotes().add(lote(fila, recepcion));
		}
	}

	/**
	 * Lo reunido de un reloj mientras se leen las filas.
	 *
	 * @param inicio inicio del vínculo
	 * @param lotes  sus sincronizaciones, la más reciente primero
	 */
	private record Acumulado(Instant inicio, List<LoteEstado> lotes) {
	}

}
