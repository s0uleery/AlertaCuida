package cl.ucn.alertacuida.adapters.persistencia.asignacion;

import cl.ucn.alertacuida.application.asignacion.puerto.Emparejamientos;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Códigos de emparejamiento pendientes en la tabla emparejamiento_reloj (V15). */
@Repository
public class EmparejamientosJdbc implements Emparejamientos {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public EmparejamientosJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Si el código choca con otro pendiente (UNIQUE), no inserta y responde false. */
	@Override
	public boolean guardar(String hashCodigo, String hashToken, Instant creacion, Instant expiracion) {
		return jdbc.sql("""
				INSERT INTO emparejamiento_reloj (hash_codigo, hash_token, fecha_creacion, fecha_expiracion)
				VALUES (:codigo, :token, :creacion, :expiracion)
				ON CONFLICT (hash_codigo) DO NOTHING""")
				.param("codigo", hashCodigo)
				.param("token", hashToken)
				.param("creacion", Timestamp.from(creacion))
				.param("expiracion", Timestamp.from(expiracion))
				.update() == 1;
	}

	@Override
	public void borrarVencidos(Instant ahora) {
		jdbc.sql("DELETE FROM emparejamiento_reloj WHERE fecha_expiracion <= :ahora")
				.param("ahora", Timestamp.from(ahora))
				.update();
	}

	@Override
	public int pendientes(Instant ahora) {
		return jdbc.sql("SELECT count(*) FROM emparejamiento_reloj WHERE fecha_expiracion > :ahora")
				.param("ahora", Timestamp.from(ahora))
				.query(Integer.class)
				.single();
	}

	/** DELETE ... RETURNING: si dos personas escriben el mismo código a la vez, solo una lo usa. */
	@Override
	public Optional<String> usar(String hashCodigo, Instant ahora) {
		return jdbc.sql("""
				DELETE FROM emparejamiento_reloj WHERE hash_codigo = :codigo AND fecha_expiracion > :ahora
				RETURNING hash_token""")
				.param("codigo", hashCodigo)
				.param("ahora", Timestamp.from(ahora))
				.query(String.class)
				.optional();
	}

	@Override
	public boolean pendiente(String hashToken, Instant ahora) {
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM emparejamiento_reloj
				WHERE hash_token = :token AND fecha_expiracion > :ahora)""")
				.param("token", hashToken)
				.param("ahora", Timestamp.from(ahora))
				.query(Boolean.class)
				.single();
	}

}
