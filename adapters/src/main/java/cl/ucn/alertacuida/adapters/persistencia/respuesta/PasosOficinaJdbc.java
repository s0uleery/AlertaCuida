package cl.ucn.alertacuida.adapters.persistencia.respuesta;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.respuesta.modelo.ContactoAvisado;
import cl.ucn.alertacuida.application.respuesta.modelo.EstadoOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoLlamadaOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoVisita;
import cl.ucn.alertacuida.application.respuesta.puerto.PasosOficina;
import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Pasos de la oficina en atencion_alerta (columnas de V19). Cada UPDATE repite en su WHERE quién puede dar el paso
 * (el responsable), en qué etapa y con la alerta abierta: si dos pasos llegan a la vez, el segundo no afecta filas.
 */
@Repository
public class PasosOficinaJdbc implements PasosOficina {

	/** Lo que exigen todos los pasos: los da el responsable, con la alerta sin cerrar. */
	private static final String DEL_RESPONSABLE = """
			WHERE id_alerta = :alerta AND id_usuario_responsable = :usuario AND fecha_hora_cierre IS NULL""";

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra el nombre del contacto y cifra las observaciones
	 */
	public PasosOficinaJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public EstadoOficina buscar(UUID idAlerta) {
		return jdbc.sql("""
				SELECT t.id_usuario_responsable, u.nombre_profesional, t.resultado_llamada,
					t.fecha_hora_personal_asignado, t.fecha_hora_sin_disponibilidad, t.fecha_hora_llegada_personal,
					t.resultado_visita, t.fecha_hora_cierre, t.clasificacion
				FROM atencion_alerta t
				LEFT JOIN usuario u ON u.id_usuario = t.id_usuario_responsable
				WHERE t.id_alerta = :alerta""")
				.param("alerta", idAlerta)
				.query((fila, n) -> estado(fila))
				.optional()
				.orElse(EstadoOficina.VACIO);
	}

	@Override
	public boolean registrarLlamada(UUID idAlerta, UUID idUsuario, ResultadoLlamadaOficina resultado, Instant fecha) {
		return jdbc.sql("""
				UPDATE atencion_alerta SET resultado_llamada = :resultado, fecha_hora_resultado_llamada = :fecha
				""" + DEL_RESPONSABLE + " AND resultado_llamada IS NULL")
				.param("resultado", resultado.name())
				.param("fecha", Timestamp.from(fecha))
				.param("alerta", idAlerta)
				.param("usuario", idUsuario)
				.update() == 1;
	}

	@Override
	public boolean asignarPersonal(UUID idAlerta, UUID idUsuario, Instant fecha) {
		return coordinarVisita("fecha_hora_personal_asignado", idAlerta, idUsuario, fecha);
	}

	@Override
	public boolean sinDisponibilidad(UUID idAlerta, UUID idUsuario, Instant fecha) {
		return coordinarVisita("fecha_hora_sin_disponibilidad", idAlerta, idUsuario, fecha);
	}

	@Override
	public boolean registrarLlegada(UUID idAlerta, UUID idUsuario, Instant fecha) {
		return jdbc.sql("UPDATE atencion_alerta SET fecha_hora_llegada_personal = :fecha " + DEL_RESPONSABLE
				+ " AND fecha_hora_personal_asignado IS NOT NULL AND fecha_hora_llegada_personal IS NULL")
				.param("fecha", Timestamp.from(fecha))
				.param("alerta", idAlerta)
				.param("usuario", idUsuario)
				.update() == 1;
	}

	@Override
	public boolean registrarVisita(UUID idAlerta, UUID idUsuario, ResultadoVisita resultado, Instant fecha) {
		return jdbc.sql("""
				UPDATE atencion_alerta SET resultado_visita = :resultado, fecha_hora_resultado_visita = :fecha
				""" + DEL_RESPONSABLE + " AND fecha_hora_llegada_personal IS NOT NULL AND resultado_visita IS NULL")
				.param("resultado", resultado.name())
				.param("fecha", Timestamp.from(fecha))
				.param("alerta", idAlerta)
				.param("usuario", idUsuario)
				.update() == 1;
	}

	/** Lee las observaciones con FOR UPDATE, para que dos comentarios a la vez no se pisen. */
	@Override
	public boolean agregarComentario(UUID idAlerta, UUID idUsuario, String texto) {
		// list() y no optional(): optional() no acepta una columna en NULL
		List<String> filas = jdbc.sql("""
				SELECT observaciones_cierre FROM atencion_alerta
				WHERE id_alerta = :alerta AND id_usuario_responsable = :usuario AND fecha_hora_cierre IS NOT NULL
				FOR UPDATE""")
				.param("alerta", idAlerta)
				.param("usuario", idUsuario)
				.query((fila, n) -> fila.getString(1))
				.list();
		if (filas.isEmpty()) {
			return false;
		}
		String antes = cifrado.descifrar(filas.getFirst(), ColumnaCifrada.ATENCION_OBSERVACIONES);
		String despues = antes == null || antes.isBlank() ? texto : antes + "\n\n" + texto;
		return jdbc.sql("UPDATE atencion_alerta SET observaciones_cierre = :obs WHERE id_alerta = :alerta")
				.param("obs", cifrado.cifrar(despues, ColumnaCifrada.ATENCION_OBSERVACIONES))
				.param("alerta", idAlerta)
				.update() == 1;
	}

	/** Solo avisos a la lista del paciente que sí se enviaron; el más reciente. */
	@Override
	public Optional<ContactoAvisado> ultimoContactoAvisado(UUID idAlerta) {
		return jdbc.sql("""
				SELECT c.nombre, i.fecha_hora_envio
				FROM registro_intento i
				JOIN contacto_paciente cp ON cp.id_contacto_paciente = i.id_contacto_paciente
				JOIN contacto c ON c.id_contacto = cp.id_contacto
				WHERE i.id_alerta = :alerta AND i.fecha_hora_envio IS NOT NULL
				ORDER BY i.fecha_hora_envio DESC LIMIT 1""")
				.param("alerta", idAlerta)
				.query((fila, n) -> new ContactoAvisado(
						cifrado.descifrar(fila.getString("nombre"), ColumnaCifrada.CONTACTO_NOMBRE),
						fila.getTimestamp("fecha_hora_envio").toInstant()))
				.optional();
	}

	/**
	 * Asignar personal o decir que no hay: solo si la persona necesita atención o no contestó, y una sola de las dos
	 * (regla atencion_visita_coordinada de V19).
	 *
	 * @param columna la fecha que se llena
	 */
	private boolean coordinarVisita(String columna, UUID idAlerta, UUID idUsuario, Instant fecha) {
		return jdbc.sql("UPDATE atencion_alerta SET " + columna + " = :fecha " + DEL_RESPONSABLE + """
				 AND resultado_llamada IN ('NECESITA_ATENCION', 'NO_CONTESTA')
				AND fecha_hora_personal_asignado IS NULL AND fecha_hora_sin_disponibilidad IS NULL""")
				.param("fecha", Timestamp.from(fecha))
				.param("alerta", idAlerta)
				.param("usuario", idUsuario)
				.update() == 1;
	}

	/** @return la fila leída como estado de la oficina */
	private static EstadoOficina estado(ResultSet fila) throws SQLException {
		String llamada = fila.getString("resultado_llamada");
		String visita = fila.getString("resultado_visita");
		String clasificacion = fila.getString("clasificacion");
		return new EstadoOficina(fila.getObject("id_usuario_responsable", UUID.class),
				fila.getString("nombre_profesional"),
				llamada == null ? null : ResultadoLlamadaOficina.valueOf(llamada),
				instante(fila, "fecha_hora_personal_asignado"), instante(fila, "fecha_hora_sin_disponibilidad"),
				instante(fila, "fecha_hora_llegada_personal"), visita == null ? null : ResultadoVisita.valueOf(visita),
				instante(fila, "fecha_hora_cierre"),
				clasificacion == null ? null : ClasificacionAlerta.valueOf(clasificacion));
	}

	/** @return la fecha de esa columna, o null */
	private static Instant instante(ResultSet fila, String columna) throws SQLException {
		Timestamp valor = fila.getTimestamp(columna);
		return valor == null ? null : valor.toInstant();
	}

}
