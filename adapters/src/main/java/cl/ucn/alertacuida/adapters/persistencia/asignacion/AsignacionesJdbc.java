package cl.ucn.alertacuida.adapters.persistencia.asignacion;

import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionHistorial;
import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionVigente;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Vínculos reloj-paciente en la tabla asignacion_reloj. Vigente = sin fecha_fin.
 *
 * <p>Las restricciones EXCLUDE de V4 impiden que dos vínculos del mismo reloj o del mismo paciente
 * se crucen en el tiempo: si dos personas vinculan a la vez, la segunda falla al guardar.
 */
@Repository
public class AsignacionesJdbc implements Asignaciones {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public AsignacionesJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<AsignacionVigente> vigenteDelPaciente(UUID idPaciente) {
		return vigente("id_paciente", idPaciente);
	}

	@Override
	public Optional<AsignacionVigente> vigenteDelReloj(UUID idDispositivo) {
		return vigente("id_dispositivo", idDispositivo);
	}

	@Override
	public Map<UUID, String> relojesVigentesPorPaciente() {
		Map<UUID, String> relojes = new HashMap<>();
		jdbc.sql("""
				SELECT a.id_paciente, s.identificador_reloj
				FROM asignacion_reloj a JOIN smartwatch s ON s.id_dispositivo = a.id_dispositivo
				WHERE a.fecha_fin IS NULL""")
				.query(fila -> {
					relojes.put(fila.getObject("id_paciente", UUID.class), fila.getString("identificador_reloj"));
				});
		return relojes;
	}

	@Override
	public List<AsignacionHistorial> historialDelPaciente(UUID idPaciente) {
		return jdbc.sql("""
				SELECT a.id_asignacion, a.id_dispositivo, s.identificador_reloj, a.fecha_inicio, a.fecha_fin
				FROM asignacion_reloj a JOIN smartwatch s ON s.id_dispositivo = a.id_dispositivo
				WHERE a.id_paciente = :paciente
				ORDER BY a.fecha_inicio DESC""")
				.param("paciente", idPaciente)
				.query((fila, n) -> new AsignacionHistorial(fila.getObject("id_asignacion", UUID.class),
						fila.getObject("id_dispositivo", UUID.class), fila.getString("identificador_reloj"),
						instante(fila, "fecha_inicio"), instante(fila, "fecha_fin")))
				.list();
	}

	/** @throws Conflicto si la base rechaza el vínculo por cruzarse con otro (EXCLUDE) */
	@Override
	public UUID abrir(UUID idDispositivo, UUID idPaciente, Instant inicio, String hashToken) {
		try {
			return jdbc.sql("""
					INSERT INTO asignacion_reloj (id_dispositivo, id_paciente, fecha_inicio, hash_token_reloj)
					VALUES (:reloj, :paciente, :inicio, :hash)
					RETURNING id_asignacion""")
					.param("reloj", idDispositivo)
					.param("paciente", idPaciente)
					.param("inicio", Timestamp.from(inicio))
					.param("hash", hashToken)
					.query(UUID.class)
					.single();
		} catch (DataIntegrityViolationException cruce) {
			throw new Conflicto("El reloj o el usuario ya tienen un vínculo vigente");
		}
	}

	@Override
	public void cerrar(UUID idAsignacion, Instant fin) {
		jdbc.sql("UPDATE asignacion_reloj SET fecha_fin = :fin WHERE id_asignacion = :id AND fecha_fin IS NULL")
				.param("fin", Timestamp.from(fin))
				.param("id", idAsignacion)
				.update();
	}

	@Override
	public void cambiarToken(UUID idAsignacion, String hashToken) {
		jdbc.sql("UPDATE asignacion_reloj SET hash_token_reloj = :hash WHERE id_asignacion = :id")
				.param("hash", hashToken)
				.param("id", idAsignacion)
				.update();
	}

	/** @param columna id_paciente o id_dispositivo (constante, nunca viene del usuario) */
	private Optional<AsignacionVigente> vigente(String columna, UUID id) {
		return jdbc.sql("SELECT id_asignacion, id_dispositivo, id_paciente FROM asignacion_reloj WHERE "
				+ columna + " = :id AND fecha_fin IS NULL")
				.param("id", id)
				.query((fila, n) -> new AsignacionVigente(fila.getObject("id_asignacion", UUID.class),
						fila.getObject("id_dispositivo", UUID.class), fila.getObject("id_paciente", UUID.class)))
				.optional();
	}

	private static Instant instante(ResultSet fila, String columna) throws SQLException {
		Timestamp valor = fila.getTimestamp(columna);
		return valor == null ? null : valor.toInstant();
	}

}
