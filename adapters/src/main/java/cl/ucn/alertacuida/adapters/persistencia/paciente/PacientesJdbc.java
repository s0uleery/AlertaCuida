package cl.ucn.alertacuida.adapters.persistencia.paciente;

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
 * dirección) se guardan por ahora sin cifrar. Cuando llegue el cifrado, solo cambia esta
 * clase: los casos de uso no se enteran. Por lo mismo, la lista se ordena aquí y no con ORDER BY.
 */
@Repository
public class PacientesJdbc implements Pacientes {

	private static final String COLUMNAS = """
			id_paciente, rut, nombres, apellidos, alias, fecha_nacimiento, sexo, direccion, nivel_dependencia,
			fecha_consentimiento, fecha_ingreso, fecha_baja""";

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public PacientesJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
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

	/** Ordena por apellidos y nombres en Java: cuando vayan cifrados, la base no podrá ordenarlos. */
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

	/** Los mismos parámetros para el INSERT y el UPDATE. */
	private static JdbcClient.StatementSpec parametros(JdbcClient.StatementSpec sql, DatosPaciente datos,
			String runHash) {
		// TODO: cifrar rut, nombres, apellidos, fecha_nacimiento y direccion
		return sql.param("rut", datos.run())
				.param("rutHash", runHash)
				.param("nombres", datos.nombres())
				.param("apellidos", datos.apellidos())
				.param("alias", datos.alias())
				// Es texto porque irá cifrada: se guarda en formato ISO (2026-10-22)
				.param("nacimiento", datos.fechaNacimiento() == null ? null : datos.fechaNacimiento().toString())
				.param("sexo", datos.sexo() == null ? null : datos.sexo().name())
				.param("direccion", datos.direccion())
				.param("nivel", datos.nivelDependencia() == null ? null : datos.nivelDependencia().name())
				.param("consentimiento", datos.fechaConsentimiento())
				.param("ingreso", datos.fechaIngreso());
	}

	/** Arma un {@link Paciente} desde una fila. La fecha de nacimiento es texto (irá cifrada) en formato ISO. */
	private static Paciente paciente(ResultSet fila) throws SQLException {
		String nacimiento = fila.getString("fecha_nacimiento");
		String sexo = fila.getString("sexo");
		String nivel = fila.getString("nivel_dependencia");
		DatosPaciente datos = new DatosPaciente(fila.getString("rut"), fila.getString("nombres"),
				fila.getString("apellidos"), fila.getString("alias"),
				nacimiento == null ? null : LocalDate.parse(nacimiento),
				sexo == null ? null : Sexo.valueOf(sexo), fila.getString("direccion"),
				nivel == null ? null : NivelDependencia.valueOf(nivel),
				fila.getObject("fecha_consentimiento", LocalDate.class),
				fila.getObject("fecha_ingreso", LocalDate.class));
		return new Paciente(fila.getObject("id_paciente", UUID.class), datos,
				fila.getObject("fecha_baja", LocalDate.class));
	}

}
