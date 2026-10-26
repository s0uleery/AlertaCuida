package cl.ucn.alertacuida.adapters.persistencia.paciente;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.paciente.modelo.Paciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.paciente.NivelDependencia;
import cl.ucn.alertacuida.domain.paciente.Sexo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Pacientes en la tabla paciente.
 *
 * <p>Las columnas marcadas "cifrado" en V2 (RUN, nombres, apellidos, fecha de nacimiento,
 * dirección) se cifran aquí al guardar y se descifran al leer: los casos de uso no se enteran.
 * Por lo mismo, la lista se ordena aquí y no con ORDER BY: la base no puede ordenar datos cifrados.
 */
@Repository
public class PacientesJdbc implements Pacientes {

	private static final String COLUMNAS = """
			id_paciente, rut, nombres, apellidos, alias, fecha_nacimiento, sexo, direccion, nivel_dependencia,
			fecha_consentimiento, fecha_ingreso, fecha_baja""";

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado cifra y descifra los datos personales
	 */
	public PacientesJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public boolean existeRunHash(String runHash, UUID excepto) {
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM paciente WHERE rut_hash = :hash
					AND (CAST(:excepto AS uuid) IS NULL OR id_paciente <> :excepto))""")
				.param("hash", runHash)
				.param("excepto", excepto)
				.query(Boolean.class)
				.single();
	}

	@Override
	public UUID crear(DatosPaciente datos, String runHash) {
		return parametros(jdbc.sql("""
				INSERT INTO paciente (rut, rut_hash, nombres, apellidos, alias, fecha_nacimiento, sexo, direccion,
					nivel_dependencia, fecha_consentimiento, fecha_ingreso)
				VALUES (:rut, :rutHash, :nombres, :apellidos, :alias, :nacimiento, :sexo, :direccion, :nivel,
					:consentimiento, :ingreso)
				RETURNING id_paciente"""), datos, runHash)
				.query(UUID.class)
				.single();
	}

	@Override
	public void actualizar(UUID id, DatosPaciente datos, String runHash) {
		parametros(jdbc.sql("""
				UPDATE paciente SET rut = :rut, rut_hash = :rutHash, nombres = :nombres, apellidos = :apellidos,
					alias = :alias, fecha_nacimiento = :nacimiento, sexo = :sexo, direccion = :direccion,
					nivel_dependencia = :nivel, fecha_consentimiento = :consentimiento, fecha_ingreso = :ingreso
				WHERE id_paciente = :id"""), datos, runHash)
				.param("id", id)
				.update();
	}

	@Override
	public Optional<Paciente> buscar(UUID id) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM paciente WHERE id_paciente = :id")
				.param("id", id)
				.query((fila, n) -> paciente(fila))
				.optional();
	}

	/** Ordena por apellidos y nombres en Java: van cifrados y la base no puede ordenarlos. */
	@Override
	public List<Paciente> listar(boolean incluirBajas) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM paciente WHERE :todos OR fecha_baja IS NULL")
				.param("todos", incluirBajas)
				.query((fila, n) -> paciente(fila))
				.list().stream()
				.sorted(Comparator.comparing((Paciente p) -> Objects.toString(p.datos().apellidos(), ""))
						.thenComparing(p -> Objects.toString(p.datos().nombres(), "")))
				.toList();
	}

	@Override
	public void darDeBaja(UUID id, LocalDate fecha) {
		jdbc.sql("UPDATE paciente SET fecha_baja = :fecha WHERE id_paciente = :id")
				.param("fecha", fecha)
				.param("id", id)
				.update();
	}

	/** Los mismos parámetros para el INSERT y el UPDATE, con los datos personales cifrados. */
	private JdbcClient.StatementSpec parametros(JdbcClient.StatementSpec sql, DatosPaciente datos, String runHash) {
		return sql.param("rut", cifrado.cifrar(datos.run(), ColumnaCifrada.PACIENTE_RUT))
				.param("rutHash", runHash)
				.param("nombres", cifrado.cifrar(datos.nombres(), ColumnaCifrada.PACIENTE_NOMBRES))
				.param("apellidos", cifrado.cifrar(datos.apellidos(), ColumnaCifrada.PACIENTE_APELLIDOS))
				.param("alias", datos.alias())
				// Es texto porque va cifrada: antes de cifrar, en formato ISO (2026-10-22)
				.param("nacimiento", cifrado.cifrar(
						datos.fechaNacimiento() == null ? null : datos.fechaNacimiento().toString(),
						ColumnaCifrada.PACIENTE_FECHA_NACIMIENTO))
				.param("sexo", datos.sexo() == null ? null : datos.sexo().name())
				.param("direccion", cifrado.cifrar(datos.direccion(), ColumnaCifrada.PACIENTE_DIRECCION))
				.param("nivel", datos.nivelDependencia() == null ? null : datos.nivelDependencia().name())
				.param("consentimiento", datos.fechaConsentimiento())
				.param("ingreso", datos.fechaIngreso());
	}

	/** Arma un {@link Paciente} desde una fila, descifrando sus datos personales. */
	private Paciente paciente(ResultSet fila) throws SQLException {
		String nacimiento = cifrado.descifrar(fila.getString("fecha_nacimiento"),
				ColumnaCifrada.PACIENTE_FECHA_NACIMIENTO);
		String sexo = fila.getString("sexo");
		String nivel = fila.getString("nivel_dependencia");
		DatosPaciente datos = new DatosPaciente(
				cifrado.descifrar(fila.getString("rut"), ColumnaCifrada.PACIENTE_RUT),
				cifrado.descifrar(fila.getString("nombres"), ColumnaCifrada.PACIENTE_NOMBRES),
				cifrado.descifrar(fila.getString("apellidos"), ColumnaCifrada.PACIENTE_APELLIDOS),
				fila.getString("alias"),
				nacimiento == null ? null : LocalDate.parse(nacimiento),
				sexo == null ? null : Sexo.valueOf(sexo),
				cifrado.descifrar(fila.getString("direccion"), ColumnaCifrada.PACIENTE_DIRECCION),
				nivel == null ? null : NivelDependencia.valueOf(nivel),
				fila.getObject("fecha_consentimiento", LocalDate.class),
				fila.getObject("fecha_ingreso", LocalDate.class));
		return new Paciente(fila.getObject("id_paciente", UUID.class), datos,
				fila.getObject("fecha_baja", LocalDate.class));
	}

}
