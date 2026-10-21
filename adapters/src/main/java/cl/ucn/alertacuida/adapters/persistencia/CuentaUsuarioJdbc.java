package cl.ucn.alertacuida.adapters.persistencia;

import cl.ucn.alertacuida.application.sesion.CuentaUsuario;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CuentaUsuarioJdbc implements CuentaUsuarioRepository {

	private final JdbcClient jdbc;

	public CuentaUsuarioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Un usuario de un rol desactivado tampoco entra. */
	@Override
	public Optional<CuentaUsuario> buscarPorRunHash(String runHash) {
		return jdbc.sql("""
				SELECT u.id_usuario, u.nombre_profesional, u.codigo_rol, u.hash_contrasena,
					u.activo AND r.activo activo
				FROM usuario u JOIN rol r ON r.codigo = u.codigo_rol
				WHERE u.rut_hash = :runHash""")
				.param("runHash", runHash)
				.query((fila, n) -> new CuentaUsuario(fila.getObject("id_usuario", UUID.class),
						fila.getString("nombre_profesional"), fila.getString("codigo_rol"),
						fila.getString("hash_contrasena"), fila.getBoolean("activo")))
				.optional();
	}

	@Override
	public Set<String> permisosDelRol(String codigoRol) {
		return new HashSet<>(jdbc.sql("SELECT modulo || ':' || accion FROM permiso_rol WHERE codigo_rol = :rol")
				.param("rol", codigoRol)
				.query(String.class)
				.list());
	}

}
