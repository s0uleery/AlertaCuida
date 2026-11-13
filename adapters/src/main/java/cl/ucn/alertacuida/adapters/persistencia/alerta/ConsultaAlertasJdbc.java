package cl.ucn.alertacuida.adapters.persistencia.alerta;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.alerta.modelo.AccionSeguimiento;
import cl.ucn.alertacuida.application.alerta.modelo.AvisoRelojRegistrado;
import cl.ucn.alertacuida.application.alerta.modelo.FilaAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.HistorialAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.IntentoRegistrado;
import cl.ucn.alertacuida.application.alerta.puerto.ConsultaAlertas;
import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import cl.ucn.alertacuida.domain.alerta.EstadoAtencion;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lectura de alertas para el dashboard. La línea de tiempo no se guarda: esta clase junta las fechas de
 * alerta, atencion_alerta, registro_intento, aviso_fcm y seguimiento. Los datos cifrados salen en claro.
 */
@Repository
public class ConsultaAlertasJdbc implements ConsultaAlertas {

	/** Columnas de una alerta, con el nombre de quien la tiene a cargo. */
	private static final String COLUMNAS = """
			a.id_alerta, a.numero, p.id_paciente, p.nombres, p.apellidos, p.fecha_nacimiento, a.tipo_evento,
			e.severidad_actual, e.estado_contacto, e.estado_atencion, r.nombre_profesional AS responsable,
			a.fecha_hora_recepcion""";

	/** Una alerta con su estado calculado, su paciente y su atención, si la tiene. */
	private static final String ORIGEN = """
			FROM alerta a
			JOIN alerta_estado e USING (id_alerta)
			JOIN asignacion_reloj v USING (id_asignacion)
			JOIN paciente p USING (id_paciente)
			LEFT JOIN atencion_alerta t USING (id_alerta)
			LEFT JOIN usuario r ON r.id_usuario = t.id_usuario_responsable
			""";

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra los datos personales
	 */
	public ConsultaAlertasJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	/** El folio desempata dos alertas recibidas a la misma hora, para que el orden sea siempre el mismo. */
	@Override
	public List<FilaAlerta> listar(int limite) {
		return jdbc.sql("SELECT " + COLUMNAS + "\n" + ORIGEN + """
				WHERE a.fecha_hora_cancelacion IS NULL
				ORDER BY (e.estado_contacto = 'SIN_RESPUESTA') DESC, a.fecha_hora_recepcion DESC, a.numero DESC
				LIMIT :limite""")
				.param("limite", limite)
				.query((fila, n) -> fila(fila))
				.list();
	}

	@Override
	public Optional<HistorialAlerta> historial(UUID idAlerta) {
		return jdbc.sql("SELECT " + COLUMNAS + """
				,
					a.fecha_hora_agravamiento, a.fecha_hora_cancelacion, a.fecha_hora_sin_respuesta, t.fecha_hora_toma,
					t.fecha_hora_cierre, s.nombre_profesional AS resolutor, t.clasificacion, t.observaciones_cierre,
					t.fecha_cierre_seguimiento
				""" + ORIGEN + """
				LEFT JOIN usuario s ON s.id_usuario = t.id_usuario_resolutor
				WHERE a.id_alerta = :id""")
				.param("id", idAlerta)
				.query((fila, n) -> historial(fila, idAlerta))
				.optional();
	}

	/** Arma el historial; las listas se leen aparte, una consulta por cada tabla. */
	private HistorialAlerta historial(ResultSet fila, UUID idAlerta) throws SQLException {
		String clasificacion = fila.getString("clasificacion");
		return new HistorialAlerta(fila(fila), instante(fila, "fecha_hora_agravamiento"),
				instante(fila, "fecha_hora_cancelacion"), instante(fila, "fecha_hora_sin_respuesta"),
				instante(fila, "fecha_hora_toma"), fila.getString("responsable"), instante(fila, "fecha_hora_cierre"),
				fila.getString("resolutor"), clasificacion == null ? null : ClasificacionAlerta.valueOf(clasificacion),
				cifrado.descifrar(fila.getString("observaciones_cierre"), ColumnaCifrada.ATENCION_OBSERVACIONES),
				instante(fila, "fecha_cierre_seguimiento"), intentos(idAlerta), avisosReloj(idAlerta),
				seguimientos(idAlerta));
	}

	/** Un aviso va a una persona de la lista o a una cuenta del equipo: el nombre sale de donde corresponda. */
	private List<IntentoRegistrado> intentos(UUID idAlerta) {
		return jdbc.sql("""
				SELECT i.id_contacto_paciente, c.nombre, u.nombre_profesional, i.canal, i.ronda, i.fecha_hora_envio,
					i.fecha_hora_entrega, i.fecha_hora_lectura, i.fecha_hora_fallo, i.motivo_fallo, i.respuesta,
					i.fecha_hora_respuesta
				FROM registro_intento i
				LEFT JOIN contacto_paciente cp USING (id_contacto_paciente)
				LEFT JOIN contacto c ON c.id_contacto = cp.id_contacto
				LEFT JOIN contacto ci ON ci.id_contacto = i.id_contacto_interno
				LEFT JOIN usuario u ON u.id_usuario = ci.id_usuario
				WHERE i.id_alerta = :id
				ORDER BY COALESCE(i.fecha_hora_envio, i.fecha_hora_fallo) NULLS LAST, i.id_intento""")
				.param("id", idAlerta)
				.query((fila, n) -> new IntentoRegistrado(fila.getObject("id_contacto_paciente", UUID.class),
						nombreContacto(fila), Canal.valueOf(fila.getString("canal")),
						fila.getObject("ronda", Integer.class), instante(fila, "fecha_hora_envio"),
						instante(fila, "fecha_hora_entrega"), instante(fila, "fecha_hora_lectura"),
						instante(fila, "fecha_hora_fallo"), fila.getString("motivo_fallo"), fila.getString("respuesta"),
						instante(fila, "fecha_hora_respuesta")))
				.list();
	}

	/** @return el nombre de la persona de la lista o, si el aviso fue al equipo, el de la cuenta */
	private String nombreContacto(ResultSet fila) throws SQLException {
		String cifradoNombre = fila.getString("nombre");
		return cifradoNombre != null ? cifrado.descifrar(cifradoNombre, ColumnaCifrada.CONTACTO_NOMBRE)
				: fila.getString("nombre_profesional");
	}

	/** @return los avisos al reloj de esa alerta */
	private List<AvisoRelojRegistrado> avisosReloj(UUID idAlerta) {
		return jdbc.sql("""
				SELECT tipo, fecha_hora_envio, fecha_hora_recepcion, intentos FROM aviso_fcm
				WHERE id_alerta = :id ORDER BY fecha_hora_envio NULLS LAST, tipo""")
				.param("id", idAlerta)
				.query((fila, n) -> new AvisoRelojRegistrado(fila.getString("tipo"), instante(fila, "fecha_hora_envio"),
						instante(fila, "fecha_hora_recepcion"), fila.getInt("intentos")))
				.list();
	}

	/** @return las notas y acciones del caso, en orden */
	private List<AccionSeguimiento> seguimientos(UUID idAlerta) {
		return jdbc.sql("""
				SELECT s.tipo, s.codigo_destino, s.nota, u.nombre_profesional, s.fecha_hora
				FROM seguimiento s JOIN usuario u USING (id_usuario)
				WHERE s.id_alerta = :id ORDER BY s.fecha_hora""")
				.param("id", idAlerta)
				.query((fila, n) -> new AccionSeguimiento(fila.getString("tipo"), fila.getString("codigo_destino"),
						cifrado.descifrar(fila.getString("nota"), ColumnaCifrada.SEGUIMIENTO_NOTA),
						fila.getString("nombre_profesional"), instante(fila, "fecha_hora")))
				.list();
	}

	/** Arma una {@link FilaAlerta} con los datos de la persona descifrados. */
	private FilaAlerta fila(ResultSet fila) throws SQLException {
		String nacimiento = cifrado.descifrar(fila.getString("fecha_nacimiento"),
				ColumnaCifrada.PACIENTE_FECHA_NACIMIENTO);
		return new FilaAlerta(fila.getObject("id_alerta", UUID.class), fila.getLong("numero"),
				fila.getObject("id_paciente", UUID.class),
				cifrado.descifrar(fila.getString("nombres"), ColumnaCifrada.PACIENTE_NOMBRES),
				cifrado.descifrar(fila.getString("apellidos"), ColumnaCifrada.PACIENTE_APELLIDOS),
				nacimiento == null ? null : LocalDate.parse(nacimiento),
				TipoEvento.valueOf(fila.getString("tipo_evento")),
				Severidad.valueOf(fila.getString("severidad_actual")),
				EstadoContacto.valueOf(fila.getString("estado_contacto")),
				EstadoAtencion.valueOf(fila.getString("estado_atencion")), fila.getString("responsable"),
				instante(fila, "fecha_hora_recepcion"));
	}

	/** @return la fecha de esa columna, o null */
	private static Instant instante(ResultSet fila, String columna) throws SQLException {
		Timestamp fecha = fila.getTimestamp(columna);
		return fecha == null ? null : fecha.toInstant();
	}

}
