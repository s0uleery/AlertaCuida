package cl.ucn.alertacuida.adapters.persistencia.condicion;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.condicion.modelo.Condicion;
import cl.ucn.alertacuida.application.condicion.puerto.Condiciones;
import cl.ucn.alertacuida.domain.paciente.TipoCondicion;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Enfermedades y situaciones en la tabla condicion_paciente. La descripción (marcada "cifrado"
 * en V2) se cifra aquí al guardar y se descifra al leer.
 */
@Repository
public class CondicionesJdbc implements Condiciones {

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado cifra y descifra la descripción
	 */
	public CondicionesJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public List<Condicion> delPaciente(UUID idPaciente) {
		return jdbc.sql("SELECT id_condicion, tipo, descripcion FROM condicion_paciente WHERE id_paciente = :id")
				.param("id", idPaciente)
				.query((fila, n) -> new Condicion(fila.getObject("id_condicion", UUID.class),
						TipoCondicion.valueOf(fila.getString("tipo")),
						cifrado.descifrar(fila.getString("descripcion"), ColumnaCifrada.CONDICION_DESCRIPCION)))
				.list();
	}

	@Override
	public UUID crear(UUID idPaciente, TipoCondicion tipo, String descripcion) {
		return jdbc.sql("""
				INSERT INTO condicion_paciente (id_paciente, tipo, descripcion)
				VALUES (:paciente, :tipo, :descripcion)
				RETURNING id_condicion""")
				.param("paciente", idPaciente)
				.param("tipo", tipo.name())
				.param("descripcion", cifrado.cifrar(descripcion, ColumnaCifrada.CONDICION_DESCRIPCION))
				.query(UUID.class)
				.single();
	}

	@Override
	public boolean eliminar(UUID idPaciente, UUID idCondicion) {
		return jdbc.sql("DELETE FROM condicion_paciente WHERE id_condicion = :condicion AND id_paciente = :paciente")
				.param("condicion", idCondicion)
				.param("paciente", idPaciente)
				.update() > 0;
	}

}
