package cl.ucn.alertacuida.adapters.persistencia.sesion;

import cl.ucn.alertacuida.application.sesion.modelo.CuentaUsuario;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lee de las tablas usuario, rol y permiso_rol lo que necesita el inicio de sesión. */
@Repository
public class CuentaUsuarioJdbc implements CuentaUsuarioRepository {

	/** Consulta común; cada búsqueda agrega su condición al final. */
	private static final String SELECCION = """
			SELECT u.id_usuario, u.nombre_profesional, u.codigo_rol, u.hash_contrasena,
				u.activo AND r.activo activo
			FROM usuario u JOIN rol r ON r.codigo = u.codigo_rol
			WHERE\s""";

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public CuentaUsuarioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Un usuario de un rol desactivado tampoco entra. */
	@Override
	public Optional<CuentaUsuario> buscarPorRunHash(String runHash) {
		return jdbc.sql(SELECCION + "u.rut_hash = :runHash")
				.param("runHash", runHash)
				.query(CuentaUsuarioJdbc::cuenta)
				.optional();
	}

	/** Un usuario de un rol desactivado tampoco renueva su sesión. */
	@Override
	public Optional<CuentaUsuario> buscarPorId(UUID id) {
		return jdbc.sql(SELECCION + "u.id_usuario = :id")
				.param("id", id)
				.query(CuentaUsuarioJdbc::cuenta)
				.optional();
	}

	/** Arma la cuenta desde una fila de {@link #SELECCION}. */
	private static CuentaUsuario cuenta(ResultSet fila, int numero) throws SQLException {
		return new CuentaUsuario(fila.getObject("id_usuario", UUID.class), fila.getString("nombre_profesional"),
				fila.getString("codigo_rol"), fila.getString("hash_contrasena"), fila.getBoolean("activo"));
	}

	@Override
	public Set<String> permisosDelRol(String codigoRol) {
		return new HashSet<>(jdbc.sql("SELECT modulo || ':' || accion FROM permiso_rol WHERE codigo_rol = :rol")
				.param("rol", codigoRol)
				.query(String.class)
				.list());
	}

}
