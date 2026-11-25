package cl.ucn.alertacuida.adapters.persistencia.reloj;

import cl.ucn.alertacuida.application.reloj.puerto.BajaRelojes;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Borrado de relojes en smartwatch. La clave foránea de asignacion_reloj no tiene cascada, así que la base
 * tampoco dejaría borrar un reloj con vínculos; aquí se revisa antes para responder con un mensaje claro.
 */
@Repository
public class BajaRelojesJdbc implements BajaRelojes {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public BajaRelojesJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean tuvoAsignaciones(UUID id) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM asignacion_reloj WHERE id_dispositivo = :id)")
				.param("id", id)
				.query(Boolean.class)
				.single();
	}

	@Override
	public boolean eliminarSinHistorial(UUID id) {
		return jdbc.sql("""
				DELETE FROM smartwatch s WHERE s.id_dispositivo = :id
					AND NOT EXISTS (SELECT 1 FROM asignacion_reloj a WHERE a.id_dispositivo = s.id_dispositivo)""")
				.param("id", id)
				.update() == 1;
	}

}
