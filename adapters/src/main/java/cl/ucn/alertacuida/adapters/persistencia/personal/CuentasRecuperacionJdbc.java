package cl.ucn.alertacuida.adapters.persistencia.personal;

import cl.ucn.alertacuida.application.personal.modelo.CuentaRecuperable;
import cl.ucn.alertacuida.application.personal.puerto.CuentasRecuperacion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Cuentas de la tabla usuario que pueden recuperar su contraseña: activas y con contraseña. Una pendiente no
 * tiene contraseña (se activa con su propio enlace) y una desactivada tiene {@code activo = false}.
 */
@Repository
public class CuentasRecuperacionJdbc implements CuentasRecuperacion {

	private static final String COLUMNAS = "id_usuario, rut_hash, nombre_profesional, correo";
	private static final String ACTIVA = "activo AND hash_contrasena IS NOT NULL";
	/** Lee la cuenta de una fila con {@link #COLUMNAS}. */
	private static final RowMapper<CuentaRecuperable> CUENTA = (fila, n) -> new CuentaRecuperable(
			fila.getObject("id_usuario", UUID.class), fila.getString("rut_hash"),
			fila.getString("nombre_profesional"), fila.getString("correo"));

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public CuentasRecuperacionJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<CuentaRecuperable> buscarActivaPorRunHash(String runHash) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE rut_hash = :hash AND " + ACTIVA)
				.param("hash", runHash)
				.query(CUENTA)
				.optional();
	}

	@Override
	public Optional<CuentaRecuperable> buscarActiva(UUID id) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE id_usuario = :id AND " + ACTIVA)
				.param("id", id)
				.query(CUENTA)
				.optional();
	}

	/** La condición de cuenta activa evita cambiarle la contraseña a una que se desactivó mientras tanto. */
	@Override
	public boolean cambiarContrasena(UUID id, String hashContrasena) {
		return jdbc.sql("UPDATE usuario SET hash_contrasena = :hash WHERE id_usuario = :id AND " + ACTIVA)
				.param("hash", hashContrasena)
				.param("id", id)
				.update() == 1;
	}

}
