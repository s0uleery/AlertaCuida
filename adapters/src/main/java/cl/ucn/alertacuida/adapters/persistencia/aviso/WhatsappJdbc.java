package cl.ucn.alertacuida.adapters.persistencia.aviso;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaWhatsapp;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Bandeja de salida de WhatsApp sobre registro_intento (canal WHATSAPP): los avisos a contactos del paciente
 * que pide el reloj y los avisos a la oficina, que van a cada coordinador como contacto interno. El número se
 * descifra aquí: el del contacto se lee de contacto y el de la cuenta, de usuario. Lo que pasa con cada envío
 * se registra con {@link CorreosJdbc}, que implementa el registro común de envíos.
 */
@Repository
public class WhatsappJdbc implements BandejaWhatsapp {

	/** Cuántos WhatsApp se toman por pasada, para que una cola grande no bloquee la tarea programada. */
	static final int POR_PASADA = 100;

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra el número de los contactos y de las cuentas
	 */
	public WhatsappJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public List<WhatsappPendiente> pendientes(Instant ahora) {
		return jdbc.sql("""
				SELECT i.id_intento, i.reintentos, i.proposito, a.numero, p.alias, a.tipo_evento, a.es_simulacro,
					e.severidad_actual, i.id_contacto_interno IS NOT NULL AS al_equipo, c.whatsapp, u.telefono
				FROM registro_intento i
				JOIN alerta a ON a.id_alerta = i.id_alerta
				JOIN alerta_estado e ON e.id_alerta = i.id_alerta
				JOIN asignacion_reloj r ON r.id_asignacion = a.id_asignacion
				JOIN paciente p ON p.id_paciente = r.id_paciente
				LEFT JOIN contacto_paciente cp ON cp.id_contacto_paciente = i.id_contacto_paciente
				LEFT JOIN contacto c ON c.id_contacto = cp.id_contacto
				LEFT JOIN contacto ci ON ci.id_contacto = i.id_contacto_interno
				LEFT JOIN usuario u ON u.id_usuario = ci.id_usuario
				WHERE i.canal = 'WHATSAPP' AND i.fecha_hora_proximo_envio <= :ahora
				ORDER BY i.fecha_hora_proximo_envio
				LIMIT :limite""")
				.param("ahora", Timestamp.from(ahora))
				.param("limite", POR_PASADA)
				.query((fila, n) -> pendiente(fila))
				.list();
	}

	/** @return el WhatsApp pendiente de la fila, con el número de destino ya descifrado */
	private WhatsappPendiente pendiente(ResultSet fila) throws SQLException {
		boolean alEquipo = fila.getBoolean("al_equipo");
		String guardado = fila.getString(alEquipo ? "telefono" : "whatsapp");
		String telefono = guardado == null ? null
				: cifrado.descifrar(guardado,
						alEquipo ? ColumnaCifrada.USUARIO_TELEFONO : ColumnaCifrada.CONTACTO_WHATSAPP);
		return new WhatsappPendiente(fila.getObject("id_intento", UUID.class), telefono, fila.getLong("numero"),
				fila.getString("alias"), TipoEvento.valueOf(fila.getString("tipo_evento")),
				Severidad.valueOf(fila.getString("severidad_actual")), fila.getBoolean("es_simulacro"),
				fila.getInt("reintentos"), alEquipo, Proposito.valueOf(fila.getString("proposito")));
	}

}
