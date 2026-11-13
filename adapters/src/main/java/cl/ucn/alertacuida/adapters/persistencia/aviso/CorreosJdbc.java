package cl.ucn.alertacuida.adapters.persistencia.aviso;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.modelo.AvisoPorEncolar;
import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.aviso.puerto.ColaCorreos;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Bandeja de salida de correos sobre registro_intento (canal CORREO). Los correos a cuentas del equipo
 * van a su contacto interno (contacto.id_usuario), que se crea la primera vez; los de respaldo a un
 * contacto del paciente van a su vínculo, con la misma ronda del WhatsApp que falló. El correo del
 * contacto externo se descifra aquí; el de la cuenta se lee de usuario.
 */
@Repository
public class CorreosJdbc implements ColaCorreos, BandejaSalida {

	/** Cuántos correos se toman por pasada, para que una cola grande no bloquee la tarea programada. */
	static final int POR_PASADA = 100;

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra el correo de los contactos externos
	 */
	public CorreosJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public List<AvisoPorEncolar> avisosAlEquipoPorEncolar(Set<TipoEvento> tipos, Instant desde) {
		if (tipos.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT a.id_alerta, u.id_usuario, a.fecha_hora_recepcion
				FROM alerta a
				LEFT JOIN atencion_alerta t ON t.id_alerta = a.id_alerta
				CROSS JOIN usuario u
				WHERE u.activo AND u.recibe_avisos
				AND NOT a.es_simulacro AND a.fecha_hora_cancelacion IS NULL AND t.fecha_hora_cierre IS NULL
				AND a.tipo_evento IN (:tipos) AND a.fecha_hora_recepcion >= :desde
				AND NOT EXISTS (SELECT 1 FROM registro_intento i
					JOIN contacto c ON c.id_contacto = i.id_contacto_interno
					WHERE i.id_alerta = a.id_alerta AND i.canal = 'CORREO' AND i.proposito = 'AVISO'
					AND c.id_usuario = u.id_usuario)
				ORDER BY a.fecha_hora_recepcion""")
				.param("tipos", tipos.stream().map(TipoEvento::name).toList())
				.param("desde", Timestamp.from(desde))
				.query((fila, n) -> new AvisoPorEncolar(fila.getObject("id_alerta", UUID.class),
						fila.getObject("id_usuario", UUID.class),
						fila.getTimestamp("fecha_hora_recepcion").toInstant()))
				.list();
	}

	@Override
	public void encolarParaCuenta(UUID idAlerta, UUID idUsuario, Instant ahora) {
		jdbc.sql("INSERT INTO contacto (id_usuario) VALUES (:usuario) ON CONFLICT (id_usuario) DO NOTHING")
				.param("usuario", idUsuario)
				.update();
		jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_interno, canal, fecha_hora_solicitud,
					fecha_hora_proximo_envio)
				SELECT gen_random_uuid(), :alerta, id_contacto, 'CORREO', :ahora, :ahora
				FROM contacto WHERE id_usuario = :usuario
				ON CONFLICT DO NOTHING""")
				.param("alerta", idAlerta)
				.param("usuario", idUsuario)
				.param("ahora", Timestamp.from(ahora))
				.update();
	}

	/** El respaldo va al mismo destino y con la misma ronda; la clave única evita repetirlo. */
	@Override
	public int encolarRespaldos(Instant desde, Instant ahora) {
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, id_contacto_interno, canal,
					ronda, fecha_hora_solicitud, fecha_hora_proximo_envio)
				SELECT gen_random_uuid(), w.id_alerta, w.id_contacto_paciente, w.id_contacto_interno, 'CORREO', w.ronda,
					:ahora, :ahora
				FROM registro_intento w
				JOIN alerta a ON a.id_alerta = w.id_alerta
				LEFT JOIN atencion_alerta t ON t.id_alerta = w.id_alerta
				LEFT JOIN contacto_paciente cp ON cp.id_contacto_paciente = w.id_contacto_paciente
				LEFT JOIN contacto c ON c.id_contacto = cp.id_contacto
				WHERE w.canal = 'WHATSAPP' AND w.fecha_hora_fallo >= :desde
				AND a.fecha_hora_cancelacion IS NULL AND t.fecha_hora_cierre IS NULL
				AND (w.id_contacto_interno IS NOT NULL OR c.correo IS NOT NULL)
				ON CONFLICT DO NOTHING""")
				.param("desde", Timestamp.from(desde))
				.param("ahora", Timestamp.from(ahora))
				.update();
	}

	@Override
	public List<CorreoPendiente> pendientes(Instant ahora) {
		return jdbc.sql("""
				SELECT i.id_intento, i.reintentos, a.numero, p.alias, a.tipo_evento, e.severidad_actual,
					i.id_contacto_interno IS NOT NULL AS al_equipo, u.correo AS correo_cuenta,
					c.correo AS correo_contacto, i.proposito = 'VIGILANTE' AS vigilante
				FROM registro_intento i
				JOIN alerta a ON a.id_alerta = i.id_alerta
				JOIN alerta_estado e ON e.id_alerta = i.id_alerta
				JOIN asignacion_reloj r ON r.id_asignacion = a.id_asignacion
				JOIN paciente p ON p.id_paciente = r.id_paciente
				LEFT JOIN contacto_paciente cp ON cp.id_contacto_paciente = i.id_contacto_paciente
				LEFT JOIN contacto c ON c.id_contacto = cp.id_contacto
				LEFT JOIN contacto ci ON ci.id_contacto = i.id_contacto_interno
				LEFT JOIN usuario u ON u.id_usuario = ci.id_usuario
				WHERE i.canal = 'CORREO' AND i.fecha_hora_proximo_envio <= :ahora
				ORDER BY i.fecha_hora_proximo_envio
				LIMIT :limite""")
				.param("ahora", Timestamp.from(ahora))
				.param("limite", POR_PASADA)
				.query((fila, n) -> pendiente(fila))
				.list();
	}

	@Override
	public void marcarEnviado(UUID idIntento, String idExterno, Instant ahora) {
		jdbc.sql("""
				UPDATE registro_intento
				SET fecha_hora_envio = :ahora, id_externo = :externo, fecha_hora_proximo_envio = NULL
				WHERE id_intento = :id""")
				.param("ahora", Timestamp.from(ahora))
				.param("externo", idExterno)
				.param("id", idIntento)
				.update();
	}

	@Override
	public void reprogramar(UUID idIntento, int reintentos, Instant proximo) {
		jdbc.sql("""
				UPDATE registro_intento SET reintentos = :reintentos, fecha_hora_proximo_envio = :proximo
				WHERE id_intento = :id""")
				.param("reintentos", reintentos)
				.param("proximo", Timestamp.from(proximo))
				.param("id", idIntento)
				.update();
	}

	@Override
	public void marcarFallido(UUID idIntento, String motivo, Instant ahora) {
		jdbc.sql("""
				UPDATE registro_intento
				SET fecha_hora_fallo = :ahora, motivo_fallo = :motivo, fecha_hora_proximo_envio = NULL
				WHERE id_intento = :id""")
				.param("ahora", Timestamp.from(ahora))
				.param("motivo", motivo)
				.param("id", idIntento)
				.update();
	}

	/** @return el correo pendiente de la fila, con el correo de destino ya descifrado */
	private CorreoPendiente pendiente(ResultSet fila) throws SQLException {
		boolean alEquipo = fila.getBoolean("al_equipo");
		String guardado = fila.getString("correo_contacto");
		String destinatario = alEquipo ? fila.getString("correo_cuenta")
				: guardado == null ? null : cifrado.descifrar(guardado, ColumnaCifrada.CONTACTO_CORREO);
		return new CorreoPendiente(fila.getObject("id_intento", UUID.class), destinatario, alEquipo,
				fila.getLong("numero"), fila.getString("alias"), TipoEvento.valueOf(fila.getString("tipo_evento")),
				Severidad.valueOf(fila.getString("severidad_actual")), fila.getInt("reintentos"),
				fila.getBoolean("vigilante"));
	}

}
