package cl.ucn.alertacuida.adapters.persistencia.reloj;

import cl.ucn.alertacuida.application.reloj.modelo.DatosTecnicosReloj;
import cl.ucn.alertacuida.application.reloj.puerto.EstadosTecnicos;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.telemetria.TipoConexion;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Estado técnico de los relojes en una sola consulta: cada reloj con su vínculo vigente (si lo tiene), la última
 * sincronización de ese vínculo y si tiene una alerta técnica real abierta (no cancelada, sin cerrar, que no sea
 * simulacro).
 */
@Repository
public class EstadosTecnicosJdbc implements EstadosTecnicos {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public EstadosTecnicosJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<DatosTecnicosReloj> todos() {
		return jdbc.sql("""
				SELECT s.id_dispositivo, v.id_paciente, v.fecha_inicio, s.fecha_vencimiento_plan,
					u.fecha_hora_recepcion, u.nivel_bateria, u.reloj_puesto, u.tipo_conexion, u.version_app,
					u.hash_configuracion,
					EXISTS (SELECT 1 FROM alerta a LEFT JOIN atencion_alerta t ON t.id_alerta = a.id_alerta
						WHERE a.id_asignacion = v.id_asignacion AND a.tipo_evento IN (:tecnicas) AND NOT a.es_simulacro
						AND a.fecha_hora_cancelacion IS NULL AND t.fecha_hora_cierre IS NULL) AS alerta_tecnica
				FROM smartwatch s
				LEFT JOIN asignacion_reloj v ON v.id_dispositivo = s.id_dispositivo AND v.fecha_fin IS NULL
				LEFT JOIN LATERAL (
					SELECT x.fecha_hora_recepcion, x.nivel_bateria, x.reloj_puesto, x.tipo_conexion, x.version_app,
						x.hash_configuracion
					FROM sincronizacion x WHERE x.id_asignacion = v.id_asignacion
					ORDER BY x.fecha_hora_recepcion DESC LIMIT 1) u ON true
				ORDER BY s.identificador_reloj""")
				.param("tecnicas", TipoEvento.tecnicas().stream().map(Enum::name).toList())
				.query((fila, n) -> datos(fila))
				.list();
	}

	/** @return la fila como datos técnicos; los que el reloj no informó quedan null */
	private static DatosTecnicosReloj datos(ResultSet fila) throws SQLException {
		Date plan = fila.getDate("fecha_vencimiento_plan");
		String conexion = fila.getString("tipo_conexion");
		return new DatosTecnicosReloj(fila.getObject("id_dispositivo", UUID.class),
				fila.getObject("id_paciente", UUID.class), instante(fila, "fecha_inicio"),
				plan == null ? null : plan.toLocalDate(), instante(fila, "fecha_hora_recepcion"),
				fila.getObject("nivel_bateria", Integer.class), fila.getObject("reloj_puesto", Boolean.class),
				conexion == null ? null : TipoConexion.valueOf(conexion), fila.getString("version_app"),
				fila.getString("hash_configuracion"), fila.getBoolean("alerta_tecnica"));
	}

	/** @return la fecha de esa columna, o null */
	private static Instant instante(ResultSet fila, String columna) throws SQLException {
		Timestamp valor = fila.getTimestamp(columna);
		return valor == null ? null : valor.toInstant();
	}

}
