package cl.ucn.alertacuida.adapters.persistencia.contacto;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.DatosPersona;
import cl.ucn.alertacuida.application.contacto.modelo.Persona;
import cl.ucn.alertacuida.application.contacto.modelo.TipoContacto;
import cl.ucn.alertacuida.application.contacto.modelo.Vinculo;
import cl.ucn.alertacuida.application.contacto.puerto.Contactos;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Personas de contacto (tabla contacto) y la lista de cada paciente (contacto_paciente). Nombre,
 * WhatsApp y correo (marcados "cifrado" en V2) se cifran aquí; se buscan por su hash.
 */
@Repository
public class ContactosJdbc implements Contactos {

	/** Persona con sus columnas, para leer con {@link #persona(ResultSet)}. */
	private static final String COLUMNAS_PERSONA =
			"c.id_contacto, c.nombre, c.whatsapp, c.correo, c.fecha_opt_in, c.fecha_revocacion_opt_in";

	/** Suma temporal a las prioridades al reordenar, para no chocar con el índice único mientras tanto. */
	private static final int DESPLAZAMIENTO = 100_000;

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado cifra y descifra nombre, WhatsApp y correo
	 */
	public ContactosJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public List<TipoContacto> tipos() {
		return jdbc.sql("SELECT codigo, nombre FROM tipo_contacto WHERE activo ORDER BY codigo = 'OTRO', nombre")
				.query((fila, n) -> new TipoContacto(fila.getString("codigo"), fila.getString("nombre")))
				.list();
	}

	@Override
	public List<ContactoDelPaciente> delPaciente(UUID idPaciente) {
		return jdbc.sql("""
				SELECT %s, cp.id_contacto_paciente, cp.codigo_tipo_contacto, cp.parentesco,
					cp.es_cuidador_principal, cp.orden_prioridad, cp.hora_inicio, cp.hora_fin, cp.activo,
					(SELECT count(*) FROM contacto_paciente otro WHERE otro.id_contacto = cp.id_contacto
						AND otro.activo AND otro.id_paciente <> cp.id_paciente) AS otros_pacientes
				FROM contacto_paciente cp JOIN contacto c ON c.id_contacto = cp.id_contacto
				WHERE cp.id_paciente = :paciente""".formatted(COLUMNAS_PERSONA))
				.param("paciente", idPaciente)
				.query((fila, n) -> new ContactoDelPaciente(fila.getObject("id_contacto_paciente", UUID.class),
						persona(fila),
						new Vinculo(fila.getString("codigo_tipo_contacto"), fila.getString("parentesco"),
								fila.getBoolean("es_cuidador_principal"), horario(fila)),
						fila.getInt("orden_prioridad"), fila.getBoolean("activo"), fila.getInt("otros_pacientes")))
				.list();
	}

	@Override
	public Optional<Persona> personaPorWhatsapp(String whatsappHash) {
		return jdbc.sql("SELECT " + COLUMNAS_PERSONA + " FROM contacto c WHERE c.whatsapp_hash = :hash")
				.param("hash", whatsappHash)
				.query((fila, n) -> persona(fila))
				.optional();
	}

	@Override
	public Optional<Persona> personaPorCorreo(String correoHash) {
		return jdbc.sql("SELECT " + COLUMNAS_PERSONA + " FROM contacto c WHERE c.correo_hash = :hash")
				.param("hash", correoHash)
				.query((fila, n) -> persona(fila))
				.optional();
	}

	@Override
	public UUID crearPersona(DatosPersona datos) {
		return jdbc.sql("""
				INSERT INTO contacto (nombre, whatsapp, whatsapp_hash, correo, correo_hash)
				VALUES (:nombre, :whatsapp, :whatsappHash, :correo, :correoHash)
				RETURNING id_contacto""")
				.params(parametros(datos))
				.query(UUID.class)
				.single();
	}

	@Override
	public void actualizarPersona(UUID idContacto, DatosPersona datos) {
		jdbc.sql("""
				UPDATE contacto SET nombre = :nombre, whatsapp = :whatsapp, whatsapp_hash = :whatsappHash,
					correo = :correo, correo_hash = :correoHash
				WHERE id_contacto = :id""")
				.params(parametros(datos))
				.param("id", idContacto)
				.update();
	}

	@Override
	public void guardarOptIn(UUID idContacto, Instant fechaOptIn, Instant fechaRevocacion) {
		jdbc.sql("UPDATE contacto SET fecha_opt_in = :optIn, fecha_revocacion_opt_in = :revocacion "
				+ "WHERE id_contacto = :id")
				.param("optIn", marca(fechaOptIn))
				.param("revocacion", marca(fechaRevocacion))
				.param("id", idContacto)
				.update();
	}

	@Override
	public UUID vincular(UUID idPaciente, UUID idContacto, Vinculo vinculo, int prioridad) {
		return jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, parentesco,
					es_cuidador_principal, orden_prioridad, hora_inicio, hora_fin)
				VALUES (:paciente, :contacto, :tipo, :parentesco, :cuidador, :prioridad, :inicio, :fin)
				RETURNING id_contacto_paciente""")
				.param("paciente", idPaciente)
				.param("contacto", idContacto)
				.param("tipo", vinculo.tipo())
				.param("parentesco", vinculo.parentesco())
				.param("cuidador", vinculo.cuidadorPrincipal())
				.param("prioridad", prioridad)
				.param("inicio", hora(vinculo.horario(), true))
				.param("fin", hora(vinculo.horario(), false))
				.query(UUID.class)
				.single();
	}

	@Override
	public void actualizarVinculo(UUID idContactoPaciente, Vinculo vinculo) {
		jdbc.sql("""
				UPDATE contacto_paciente SET codigo_tipo_contacto = :tipo, parentesco = :parentesco,
					es_cuidador_principal = :cuidador, hora_inicio = :inicio, hora_fin = :fin
				WHERE id_contacto_paciente = :id""")
				.param("tipo", vinculo.tipo())
				.param("parentesco", vinculo.parentesco())
				.param("cuidador", vinculo.cuidadorPrincipal())
				.param("inicio", hora(vinculo.horario(), true))
				.param("fin", hora(vinculo.horario(), false))
				.param("id", idContactoPaciente)
				.update();
	}

	@Override
	public void quitarCuidadorPrincipal(UUID idPaciente) {
		jdbc.sql("UPDATE contacto_paciente SET es_cuidador_principal = false "
				+ "WHERE id_paciente = :paciente AND es_cuidador_principal")
				.param("paciente", idPaciente)
				.update();
	}

	@Override
	public void desactivar(UUID idContactoPaciente) {
		jdbc.sql("UPDATE contacto_paciente SET activo = false, es_cuidador_principal = false "
				+ "WHERE id_contacto_paciente = :id")
				.param("id", idContactoPaciente)
				.update();
	}

	@Override
	public void reactivar(UUID idContactoPaciente, int prioridad) {
		jdbc.sql("UPDATE contacto_paciente SET activo = true, orden_prioridad = :prioridad "
				+ "WHERE id_contacto_paciente = :id")
				.param("prioridad", prioridad)
				.param("id", idContactoPaciente)
				.update();
	}

	@Override
	public void reordenar(UUID idPaciente, List<UUID> orden) {
		// El índice único de prioridad no es diferible: primero se apartan todas y luego se asignan
		jdbc.sql("UPDATE contacto_paciente SET orden_prioridad = orden_prioridad + :desplazamiento "
				+ "WHERE id_paciente = :paciente AND activo")
				.param("desplazamiento", DESPLAZAMIENTO)
				.param("paciente", idPaciente)
				.update();
		for (int i = 0; i < orden.size(); i++) {
			jdbc.sql("UPDATE contacto_paciente SET orden_prioridad = :prioridad "
					+ "WHERE id_contacto_paciente = :id AND id_paciente = :paciente")
					.param("prioridad", i + 1)
					.param("id", orden.get(i))
					.param("paciente", idPaciente)
					.update();
		}
	}

	/** @return la persona de la fila, con sus datos descifrados */
	private Persona persona(ResultSet fila) throws SQLException {
		return new Persona(fila.getObject("id_contacto", UUID.class),
				cifrado.descifrar(fila.getString("nombre"), ColumnaCifrada.CONTACTO_NOMBRE),
				cifrado.descifrar(fila.getString("whatsapp"), ColumnaCifrada.CONTACTO_WHATSAPP),
				cifrado.descifrar(fila.getString("correo"), ColumnaCifrada.CONTACTO_CORREO),
				instante(fila, "fecha_opt_in"), instante(fila, "fecha_revocacion_opt_in"));
	}

	/** @return los parámetros de la persona, con sus datos cifrados */
	private Map<String, Object> parametros(DatosPersona datos) {
		Map<String, Object> parametros = new HashMap<>();
		parametros.put("nombre", cifrado.cifrar(datos.nombre(), ColumnaCifrada.CONTACTO_NOMBRE));
		parametros.put("whatsapp", cifrado.cifrar(datos.whatsapp(), ColumnaCifrada.CONTACTO_WHATSAPP));
		parametros.put("whatsappHash", datos.whatsappHash());
		parametros.put("correo", cifrado.cifrar(datos.correo(), ColumnaCifrada.CONTACTO_CORREO));
		parametros.put("correoHash", datos.correoHash());
		return parametros;
	}

	/** @return el horario de la fila, o vacío si es todo el día */
	private static Horario horario(ResultSet fila) throws SQLException {
		Time inicio = fila.getTime("hora_inicio");
		Time fin = fila.getTime("hora_fin");
		return inicio == null ? null : new Horario(inicio.toLocalTime(), fin.toLocalTime());
	}

	/** @return la hora de inicio o de término como Time para JDBC, o null si es todo el día */
	private static Time hora(Horario horario, boolean inicio) {
		if (horario == null) {
			return null;
		}
		return Time.valueOf(inicio ? horario.inicio() : horario.fin());
	}

	/** @return la fecha de la columna, o null */
	private static Instant instante(ResultSet fila, String columna) throws SQLException {
		Timestamp valor = fila.getTimestamp(columna);
		return valor == null ? null : valor.toInstant();
	}

	/** @return el instante como Timestamp para JDBC, o null */
	private static Timestamp marca(Instant instante) {
		return instante == null ? null : Timestamp.from(instante);
	}

}
