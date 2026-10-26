package cl.ucn.alertacuida.adapters.persistencia.reloj;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.reloj.modelo.DatosReloj;
import cl.ucn.alertacuida.application.reloj.modelo.Reloj;
import cl.ucn.alertacuida.application.reloj.modelo.RelojConPaciente;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Inventario de relojes en la tabla smartwatch. */
@Repository
public class RelojesJdbc implements Relojes {

	private static final String COLUMNAS = """
			s.id_dispositivo, s.identificador_reloj, s.modelo, s.iccid, s.numero_sim,
			s.fecha_vencimiento_plan, s.estado""";

	/** ICCID y número de SIM también son únicos (V4): si chocan, la base lo avisa al guardar. */
	private static final String SIM_REPETIDA = "Ya existe otro reloj con ese identificador, ICCID o número de SIM";

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra el nombre del paciente que usa cada reloj
	 */
	public RelojesJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public boolean existeIdentificador(String identificadorReloj, UUID excepto) {
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM smartwatch WHERE identificador_reloj = :identificador
					AND (CAST(:excepto AS uuid) IS NULL OR id_dispositivo <> :excepto))""")
				.param("identificador", identificadorReloj)
				.param("excepto", excepto)
				.query(Boolean.class)
				.single();
	}

	@Override
	public UUID crear(DatosReloj datos) {
		try {
			return parametros(jdbc.sql("""
					INSERT INTO smartwatch (identificador_reloj, modelo, iccid, numero_sim, fecha_vencimiento_plan,
						estado)
					VALUES (:identificador, :modelo, :iccid, :numeroSim, :vencimiento, :estado)
					RETURNING id_dispositivo"""), datos)
					.query(UUID.class)
					.single();
		} catch (DuplicateKeyException repetido) {
			throw new Conflicto(SIM_REPETIDA);
		}
	}

	@Override
	public void actualizar(UUID id, DatosReloj datos) {
		try {
			parametros(jdbc.sql("""
					UPDATE smartwatch SET identificador_reloj = :identificador, modelo = :modelo, iccid = :iccid,
						numero_sim = :numeroSim, fecha_vencimiento_plan = :vencimiento, estado = :estado
					WHERE id_dispositivo = :id"""), datos)
					.param("id", id)
					.update();
		} catch (DuplicateKeyException repetido) {
			throw new Conflicto(SIM_REPETIDA);
		}
	}

	@Override
	public void cambiarEstado(UUID id, EstadoReloj estado) {
		jdbc.sql("UPDATE smartwatch SET estado = :estado WHERE id_dispositivo = :id")
				.param("estado", estado.name())
				.param("id", id)
				.update();
	}

	@Override
	public Optional<Reloj> buscar(UUID id) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM smartwatch s WHERE s.id_dispositivo = :id")
				.param("id", id)
				.query((fila, n) -> reloj(fila))
				.optional();
	}

	/** Une cada reloj con su asignación vigente (si tiene) y el nombre de ese paciente. */
	@Override
	public List<RelojConPaciente> listar() {
		return jdbc.sql("SELECT " + COLUMNAS + """
				, p.id_paciente, p.nombres, p.apellidos
				FROM smartwatch s
				LEFT JOIN asignacion_reloj a ON a.id_dispositivo = s.id_dispositivo AND a.fecha_fin IS NULL
				LEFT JOIN paciente p ON p.id_paciente = a.id_paciente
				ORDER BY s.identificador_reloj""")
				.query((fila, n) -> {
					UUID idPaciente = fila.getObject("id_paciente", UUID.class);
					String nombre = idPaciente == null ? null
							: cifrado.descifrar(fila.getString("nombres"), ColumnaCifrada.PACIENTE_NOMBRES) + " "
									+ cifrado.descifrar(fila.getString("apellidos"), ColumnaCifrada.PACIENTE_APELLIDOS);
					return new RelojConPaciente(reloj(fila), idPaciente, nombre);
				})
				.list();
	}

	/** Los mismos parámetros para el INSERT y el UPDATE. */
	private static JdbcClient.StatementSpec parametros(JdbcClient.StatementSpec sql, DatosReloj datos) {
		return sql.param("identificador", datos.identificadorReloj())
				.param("modelo", datos.modelo())
				.param("iccid", datos.iccid())
				.param("numeroSim", datos.numeroSim())
				.param("vencimiento", datos.fechaVencimientoPlan())
				.param("estado", datos.estado().name());
	}

	/** Arma un {@link Reloj} desde una fila de smartwatch (con el alias "s"). */
	private static Reloj reloj(ResultSet fila) throws SQLException {
		return new Reloj(fila.getObject("id_dispositivo", UUID.class), new DatosReloj(
				fila.getString("identificador_reloj"), fila.getString("modelo"), fila.getString("iccid"),
				fila.getString("numero_sim"), fila.getObject("fecha_vencimiento_plan", LocalDate.class),
				EstadoReloj.valueOf(fila.getString("estado"))));
	}

}
