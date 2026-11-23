package cl.ucn.alertacuida.adapters.persistencia.personal;

import cl.ucn.alertacuida.application.personal.modelo.RolConPermisos;
import cl.ucn.alertacuida.application.personal.puerto.RolesPermisos;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Roles activos y sus permisos, en las tablas rol y permiso_rol (la fila existe = tiene el permiso). */
@Repository
public class RolesPermisosJdbc implements RolesPermisos {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public RolesPermisosJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<RolConPermisos> listar() {
		return jdbc.sql("SELECT codigo FROM rol WHERE activo ORDER BY es_sistema DESC, nombre")
				.query(String.class)
				.list()
				.stream()
				.map(codigo -> buscar(codigo).orElseThrow())
				.toList();
	}

	@Override
	public Optional<RolConPermisos> buscar(String codigo) {
		return jdbc.sql("SELECT codigo, nombre, es_sistema FROM rol WHERE codigo = :codigo AND activo")
				.param("codigo", codigo)
				.query((fila, n) -> new RolConPermisos(fila.getString("codigo"), fila.getString("nombre"),
						fila.getBoolean("es_sistema"), permisos(codigo)))
				.optional();
	}

	/** Borra los permisos del rol y vuelve a insertar los pedidos; corre dentro de la transacción del caso de uso. */
	@Override
	public void reemplazarPermisos(String codigo, Set<String> permisos) {
		jdbc.sql("DELETE FROM permiso_rol WHERE codigo_rol = :codigo").param("codigo", codigo).update();
		for (String permiso : permisos) {
			String[] partes = permiso.split(":");
			jdbc.sql("INSERT INTO permiso_rol (codigo_rol, modulo, accion) VALUES (:codigo, :modulo, :accion)")
					.param("codigo", codigo)
					.param("modulo", partes[0])
					.param("accion", partes[1])
					.update();
		}
	}

	/** @return los permisos del rol como "MODULO:ACCION", ordenados */
	private Set<String> permisos(String codigo) {
		return new TreeSet<>(jdbc.sql("SELECT modulo || ':' || accion FROM permiso_rol WHERE codigo_rol = :codigo")
				.param("codigo", codigo)
				.query(String.class)
				.list());
	}

}
