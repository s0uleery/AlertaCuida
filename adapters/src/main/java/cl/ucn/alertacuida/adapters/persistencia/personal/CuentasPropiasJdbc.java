package cl.ucn.alertacuida.adapters.persistencia.personal;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.personal.modelo.MiCuenta;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPropias;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** La cuenta propia en la tabla usuario, con el teléfono descifrado. */
@Repository
public class CuentasPropiasJdbc implements CuentasPropias {

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra el teléfono
	 */
	public CuentasPropiasJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	/** WhatsApp activo = opt-in vigente: aceptado y no revocado, la misma condición del aviso a la oficina. */
	@Override
	public Optional<MiCuenta> buscar(UUID id) {
		return jdbc.sql("""
				SELECT nombre_profesional, correo, telefono,
					fecha_opt_in IS NOT NULL AND fecha_revocacion_opt_in IS NULL AS whatsapp
				FROM usuario WHERE id_usuario = :id""")
				.param("id", id)
				.query((fila, n) -> new MiCuenta(fila.getString("nombre_profesional"), fila.getString("correo"),
						cifrado.descifrar(fila.getString("telefono"), ColumnaCifrada.USUARIO_TELEFONO),
						fila.getBoolean("whatsapp")))
				.optional();
	}

}
