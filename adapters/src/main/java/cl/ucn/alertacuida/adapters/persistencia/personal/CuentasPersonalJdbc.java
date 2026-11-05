package cl.ucn.alertacuida.adapters.persistencia.personal;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.personal.error.CuentaDuplicada;
import cl.ucn.alertacuida.application.personal.modelo.CuentaPendiente;
import cl.ucn.alertacuida.application.personal.modelo.NuevaCuenta;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Cuentas del personal en la tabla usuario. Una cuenta pendiente es la que no tiene contraseña
 * (nota de USUARIO en docs/modelo-datos.dbml): así no hace falta una columna de estado aparte.
 */
@Repository
public class CuentasPersonalJdbc implements CuentasPersonal {

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado cifra el RUN y el teléfono antes de guardarlos
	 */
	public CuentasPersonalJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	/** @param runHash HMAC del RUN normalizado */
	@Override
	public boolean existeRunHash(String runHash) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM usuario WHERE rut_hash = :hash)")
				.param("hash", runHash)
				.query(Boolean.class)
				.single();
	}

	/** @param correo en minúsculas, como se guarda */
	@Override
	public boolean existeCorreo(String correo) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM usuario WHERE correo = :correo)")
				.param("correo", correo)
				.query(Boolean.class)
				.single();
	}

	@Override
	public boolean rolActivo(String codigoRol) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM rol WHERE codigo = :rol AND activo)")
				.param("rol", codigoRol)
				.query(Boolean.class)
				.single();
	}

	/**
	 * Guarda la cuenta inactiva y sin contraseña.
	 *
	 * @return el identificador que le dio la base
	 * @throws CuentaDuplicada si otra petición creó el mismo RUN o correo entre la revisión y el INSERT
	 */
	@Override
	public UUID crear(NuevaCuenta cuenta) {
		try {
			return jdbc.sql("""
					INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, cargo, correo, telefono,
						fecha_opt_in)
					VALUES (:rol, :rut, :rutHash, :nombre, :cargo, :correo, :telefono, :optIn)
					RETURNING id_usuario""")
					.param("rol", cuenta.codigoRol())
					.param("rut", cifrado.cifrar(cuenta.run(), ColumnaCifrada.USUARIO_RUT))
					.param("rutHash", cuenta.runHash())
					.param("nombre", cuenta.nombre())
					.param("cargo", cuenta.cargo())
					.param("correo", cuenta.correo())
					.param("telefono", cifrado.cifrar(cuenta.telefono(), ColumnaCifrada.USUARIO_TELEFONO))
					.param("optIn", cuenta.fechaOptIn() == null ? null : Timestamp.from(cuenta.fechaOptIn()))
					.query(UUID.class)
					.single();
		} catch (DuplicateKeyException duplicada) {
			throw new CuentaDuplicada("Ya existe una cuenta con ese RUN o ese correo");
		}
	}

	@Override
	public Optional<CuentaPendiente> buscarPendiente(UUID id) {
		return jdbc.sql("SELECT id_usuario, rut_hash FROM usuario WHERE id_usuario = :id AND hash_contrasena IS NULL")
				.param("id", id)
				.query((fila, n) -> new CuentaPendiente(fila.getObject("id_usuario", UUID.class),
						fila.getString("rut_hash")))
				.optional();
	}

	/**
	 * La condición {@code hash_contrasena IS NULL} hace que, si dos peticiones llegan a la vez,
	 * solo una actualice la fila.
	 */
	@Override
	public boolean activar(UUID id, String hashContrasena) {
		return jdbc.sql("""
				UPDATE usuario SET hash_contrasena = :hash, activo = true
				WHERE id_usuario = :id AND hash_contrasena IS NULL""")
				.param("hash", hashContrasena)
				.param("id", id)
				.update() == 1;
	}

}
