package cl.ucn.alertacuida.adapters.persistencia.aviso;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaWhatsapp;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Bandeja de salida de WhatsApp sobre registro_intento (canal WHATSAPP), por ahora solo de los avisos a
 * contactos del paciente que pide el reloj. El número se descifra aquí. Lo que pasa con cada envío se
 * registra con {@link CorreosJdbc}, que implementa el registro común de envíos.
 */
@Repository
public class WhatsappJdbc implements BandejaWhatsapp {

	/** Cuántos WhatsApp se toman por pasada, para que una cola grande no bloquee la tarea programada. */
	static final int POR_PASADA = 100;

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra el número de los contactos
	 */
	public WhatsappJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public List<WhatsappPendiente> pendientes(Instant ahora) {
		return jdbc.sql("""
				SELECT i.id_intento, i.reintentos, a.numero, p.alias, a.tipo_evento, a.es_simulacro,
					e.severidad_actual, c.whatsapp
				FROM registro_intento i
				JOIN alerta a ON a.id_alerta = i.id_alerta
				JOIN alerta_estado e ON e.id_alerta = i.id_alerta
				JOIN asignacion_reloj r ON r.id_asignacion = a.id_asignacion
				JOIN paciente p ON p.id_paciente = r.id_paciente
				JOIN contacto_paciente cp ON cp.id_contacto_paciente = i.id_contacto_paciente
				JOIN contacto c ON c.id_contacto = cp.id_contacto
				WHERE i.canal = 'WHATSAPP' AND i.fecha_hora_proximo_envio <= :ahora
				ORDER BY i.fecha_hora_proximo_envio
				LIMIT :limite""")
				.param("ahora", Timestamp.from(ahora))
				.param("limite", POR_PASADA)
				.query((fila, n) -> {
					String guardado = fila.getString("whatsapp");
					return new WhatsappPendiente(fila.getObject("id_intento", UUID.class),
							guardado == null ? null : cifrado.descifrar(guardado, ColumnaCifrada.CONTACTO_WHATSAPP),
							fila.getLong("numero"), fila.getString("alias"),
							TipoEvento.valueOf(fila.getString("tipo_evento")),
							Severidad.valueOf(fila.getString("severidad_actual")), fila.getBoolean("es_simulacro"),
							fila.getInt("reintentos"));
				})
				.list();
	}

}
