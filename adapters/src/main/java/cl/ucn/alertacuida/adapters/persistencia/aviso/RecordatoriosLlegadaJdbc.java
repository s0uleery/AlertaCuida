package cl.ucn.alertacuida.adapters.persistencia.aviso;

import cl.ucn.alertacuida.application.aviso.puerto.RecordatoriosLlegada;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Recordatorio de llegada sobre atencion_alerta y registro_intento. Que exista el aviso RECORDATORIO de la alerta
 * es lo que dice que ya se envió: la restricción registro_intento_interno_unico (alerta, contacto, canal,
 * propósito) evita repetirlo.
 */
@Repository
public class RecordatoriosLlegadaJdbc implements RecordatoriosLlegada {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public RecordatoriosLlegadaJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<UUID> pendientes(Instant desde, Instant hasta) {
		return jdbc.sql("""
				SELECT t.id_alerta FROM atencion_alerta t
				WHERE t.fecha_hora_personal_asignado BETWEEN :desde AND :hasta
				AND t.fecha_hora_llegada_personal IS NULL AND t.fecha_hora_cierre IS NULL
				AND NOT EXISTS (SELECT 1 FROM registro_intento r
					WHERE r.id_alerta = t.id_alerta AND r.proposito = 'RECORDATORIO')""")
				.param("desde", Timestamp.from(desde))
				.param("hasta", Timestamp.from(hasta))
				.query(UUID.class)
				.list();
	}

	/** El responsable recibe el correo como contacto interno, igual que el aviso a la oficina. */
	@Override
	public void encolar(UUID idAlerta, Instant ahora) {
		jdbc.sql("""
				INSERT INTO contacto (id_usuario)
				SELECT id_usuario_responsable FROM atencion_alerta WHERE id_alerta = :alerta
				ON CONFLICT (id_usuario) DO NOTHING""")
				.param("alerta", idAlerta)
				.update();
		jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_interno, canal, proposito,
					fecha_hora_solicitud, fecha_hora_proximo_envio)
				SELECT gen_random_uuid(), t.id_alerta, c.id_contacto, 'CORREO', 'RECORDATORIO', :ahora, :ahora
				FROM atencion_alerta t JOIN contacto c ON c.id_usuario = t.id_usuario_responsable
				WHERE t.id_alerta = :alerta
				ON CONFLICT DO NOTHING""")
				.param("alerta", idAlerta)
				.param("ahora", Timestamp.from(ahora))
				.update();
	}

}
