package cl.ucn.alertacuida.adapters.persistencia.alerta;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.alerta.modelo.AlertaAtendible;
import cl.ucn.alertacuida.application.alerta.puerto.AtencionAlertas;
import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Atención de las alertas en la tabla atencion_alerta. El {@code ON CONFLICT ... WHERE} deja que la base
 * decida: si dos personas actúan a la vez, la segunda espera a la primera y no afecta ninguna fila.
 */
@Repository
public class AtencionAlertasJdbc implements AtencionAlertas {

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado cifra las observaciones
	 */
	public AtencionAlertasJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public Optional<AlertaAtendible> buscar(UUID idAlerta) {
		return jdbc.sql("""
				SELECT id_alerta, tipo_evento, origen = 'RELOJ' AS del_reloj,
					fecha_hora_cancelacion IS NOT NULL AS cancelada
				FROM alerta WHERE id_alerta = :id""")
				.param("id", idAlerta)
				.query((fila, n) -> new AlertaAtendible(fila.getObject("id_alerta", UUID.class),
						TipoEvento.valueOf(fila.getString("tipo_evento")), fila.getBoolean("del_reloj"),
						fila.getBoolean("cancelada")))
				.optional();
	}

	/** Solo toma una alerta que nadie tomó y que no se ha cerrado. */
	@Override
	public boolean asumir(UUID idAlerta, UUID idUsuario, Instant ahora) {
		return jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, id_usuario_responsable, fecha_hora_toma)
				VALUES (:id, :usuario, :ahora)
				ON CONFLICT (id_alerta) DO UPDATE
				SET id_usuario_responsable = EXCLUDED.id_usuario_responsable, fecha_hora_toma = EXCLUDED.fecha_hora_toma
				WHERE atencion_alerta.fecha_hora_toma IS NULL AND atencion_alerta.fecha_hora_cierre IS NULL""")
				.param("id", idAlerta)
				.param("usuario", idUsuario)
				.param("ahora", Timestamp.from(ahora))
				.update() == 1;
	}

	/** Conserva al responsable y la hora de toma que ya hubiera; si no había, los completa con quien resuelve. */
	@Override
	public boolean resolver(UUID idAlerta, UUID idUsuario, ClasificacionAlerta clasificacion, String observaciones,
			Instant ahora) {
		return jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, id_usuario_responsable, fecha_hora_toma, fecha_hora_cierre,
					id_usuario_resolutor, clasificacion, observaciones_cierre)
				VALUES (:id, :usuario, :ahora, :ahora, :usuario, :clasificacion, :observaciones)
				ON CONFLICT (id_alerta) DO UPDATE SET
					id_usuario_responsable = COALESCE(atencion_alerta.id_usuario_responsable,
						EXCLUDED.id_usuario_responsable),
					fecha_hora_toma = COALESCE(atencion_alerta.fecha_hora_toma, EXCLUDED.fecha_hora_toma),
					fecha_hora_cierre = EXCLUDED.fecha_hora_cierre,
					id_usuario_resolutor = EXCLUDED.id_usuario_resolutor,
					clasificacion = EXCLUDED.clasificacion,
					observaciones_cierre = EXCLUDED.observaciones_cierre
				WHERE atencion_alerta.fecha_hora_cierre IS NULL""")
				.param("id", idAlerta)
				.param("usuario", idUsuario)
				.param("ahora", Timestamp.from(ahora))
				.param("clasificacion", clasificacion == null ? null : clasificacion.name())
				.param("observaciones", cifrado.cifrar(observaciones, ColumnaCifrada.ATENCION_OBSERVACIONES))
				.update() == 1;
	}

	@Override
	public boolean cerrarSeguimiento(UUID idAlerta, Instant ahora) {
		return jdbc.sql("""
				UPDATE atencion_alerta SET fecha_cierre_seguimiento = :ahora
				WHERE id_alerta = :id AND clasificacion = 'POSITIVA' AND fecha_cierre_seguimiento IS NULL""")
				.param("ahora", Timestamp.from(ahora))
				.param("id", idAlerta)
				.update() == 1;
	}

	/** Una fila sin fecha de envío; el envío a Firebase toma las pendientes. */
	@Override
	public void dejarAvisoResueltaPendiente(UUID idAlerta) {
		jdbc.sql("""
				INSERT INTO aviso_fcm (id_alerta, tipo) VALUES (:id, 'ALERTA_RESUELTA')
				ON CONFLICT (id_alerta, tipo) DO NOTHING""")
				.param("id", idAlerta)
				.update();
	}

}
