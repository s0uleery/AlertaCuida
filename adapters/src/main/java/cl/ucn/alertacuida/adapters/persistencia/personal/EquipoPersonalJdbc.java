package cl.ucn.alertacuida.adapters.persistencia.personal;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.personal.modelo.CuentaEquipo;
import cl.ucn.alertacuida.application.personal.modelo.EstadoCuenta;
import cl.ucn.alertacuida.application.personal.modelo.PerfilEquipo;
import cl.ucn.alertacuida.application.personal.puerto.EquipoPersonal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Cuentas del equipo ya creadas, en la tabla usuario. El estado sale de activo y hash_contrasena
 * (nota de USUARIO en docs/modelo-datos.dbml) y el último acceso, del último LOGIN en la auditoría.
 */
@Repository
public class EquipoPersonalJdbc implements EquipoPersonal {

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado cifra el teléfono antes de guardarlo
	 */
	public EquipoPersonalJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	/** El último acceso usa el índice registro_auditoria_usuario. No lee el RUN ni el teléfono cifrados. */
	@Override
	public List<PerfilEquipo> listar() {
		return jdbc.sql("""
				SELECT u.id_usuario, u.nombre_profesional, u.cargo, u.correo, u.codigo_rol, u.activo,
					u.hash_contrasena IS NOT NULL AS tiene_clave, u.recibe_avisos,
					u.telefono IS NOT NULL AS tiene_telefono,
					(SELECT max(a.fecha_hora) FROM registro_auditoria a
						WHERE a.id_usuario = u.id_usuario AND a.accion = 'LOGIN') AS ultimo_acceso
				FROM usuario u
				ORDER BY u.nombre_profesional, u.correo""")
				.query((fila, n) -> {
					Timestamp ultimo = fila.getTimestamp("ultimo_acceso");
					return new PerfilEquipo(fila.getObject("id_usuario", UUID.class),
							fila.getString("nombre_profesional"), fila.getString("cargo"), fila.getString("correo"),
							fila.getString("codigo_rol"),
							EstadoCuenta.de(fila.getBoolean("activo"), fila.getBoolean("tiene_clave")),
							ultimo == null ? null : ultimo.toInstant(), fila.getBoolean("recibe_avisos"),
							fila.getBoolean("tiene_telefono"));
				})
				.list();
	}

	@Override
	public Optional<CuentaEquipo> buscar(UUID id) {
		return jdbc.sql("""
				SELECT id_usuario, codigo_rol, activo, telefono IS NOT NULL AS tiene_telefono
				FROM usuario WHERE id_usuario = :id""")
				.param("id", id)
				.query((fila, n) -> new CuentaEquipo(fila.getObject("id_usuario", UUID.class),
						fila.getString("codigo_rol"), fila.getBoolean("activo"), fila.getBoolean("tiene_telefono")))
				.optional();
	}

	/** El administrador es el rol marcado con es_sistema, no un código escrito a mano. */
	@Override
	public boolean hayOtroAdministradorActivo(UUID excluida) {
		return jdbc.sql("""
				SELECT EXISTS (
					SELECT 1 FROM usuario u JOIN rol r ON r.codigo = u.codigo_rol
					WHERE r.es_sistema AND u.activo AND u.id_usuario <> :id)""")
				.param("id", excluida)
				.query(Boolean.class)
				.single();
	}

	@Override
	public void cambiarRol(UUID id, String codigoRol) {
		jdbc.sql("UPDATE usuario SET codigo_rol = :rol WHERE id_usuario = :id")
				.param("rol", codigoRol)
				.param("id", id)
				.update();
	}

	/** Un número nuevo reemplaza también la fecha del opt-in: el consentimiento es por número. */
	@Override
	public void guardarTelefono(UUID id, String telefono, Instant fechaOptIn) {
		jdbc.sql("UPDATE usuario SET telefono = :telefono, fecha_opt_in = :optIn WHERE id_usuario = :id")
				.param("telefono", cifrado.cifrar(telefono, ColumnaCifrada.USUARIO_TELEFONO))
				.param("optIn", fechaOptIn == null ? null : Timestamp.from(fechaOptIn))
				.param("id", id)
				.update();
	}

	@Override
	public boolean cambiarRecibeAvisos(UUID id, boolean recibe) {
		return jdbc.sql("UPDATE usuario SET recibe_avisos = :recibe WHERE id_usuario = :id")
				.param("recibe", recibe)
				.param("id", id)
				.update() == 1;
	}

}
