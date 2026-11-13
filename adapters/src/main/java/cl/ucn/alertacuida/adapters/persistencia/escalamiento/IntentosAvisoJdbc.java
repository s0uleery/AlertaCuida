package cl.ucn.alertacuida.adapters.persistencia.escalamiento;

import cl.ucn.alertacuida.application.escalamiento.modelo.IntentoExistente;
import cl.ucn.alertacuida.application.escalamiento.puerto.IntentosAviso;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Avisos que pide el reloj, en registro_intento. Las claves únicas de la tabla (event_id, y alerta +
 * contacto + canal + ronda) impiden que dos peticiones simultáneas creen el mismo aviso: la segunda no
 * inserta nada ({@code ON CONFLICT DO NOTHING}).
 */
@Repository
public class IntentosAvisoJdbc implements IntentosAviso {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public IntentosAvisoJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<IntentoExistente> porEventId(UUID eventId) {
		return jdbc.sql("SELECT id_intento, id_alerta FROM registro_intento WHERE event_id = :evento")
				.param("evento", eventId)
				.query((fila, n) -> new IntentoExistente(fila.getObject("id_intento", UUID.class),
						fila.getObject("id_alerta", UUID.class)))
				.optional();
	}

	@Override
	public Optional<UUID> mismoDestino(UUID idAlerta, UUID idContacto, Canal canal, int ronda) {
		return jdbc.sql("""
				SELECT id_intento FROM registro_intento
				WHERE id_alerta = :alerta AND id_contacto_paciente = :contacto AND canal = :canal AND ronda = :ronda""")
				.param("alerta", idAlerta)
				.param("contacto", idContacto)
				.param("canal", canal.name())
				.param("ronda", ronda)
				.query(UUID.class)
				.optional();
	}

	/** Pendiente desde ahora: la tarea programada del canal lo envía en su siguiente pasada. */
	@Override
	public Optional<UUID> encolar(UUID eventId, UUID idAlerta, UUID idContacto, Canal canal, int ronda,
			Instant ahora) {
		return jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_paciente, canal, ronda,
					fecha_hora_solicitud, fecha_hora_proximo_envio)
				VALUES (:evento, :alerta, :contacto, :canal, :ronda, :ahora, :ahora)
				ON CONFLICT DO NOTHING
				RETURNING id_intento""")
				.param("evento", eventId)
				.param("alerta", idAlerta)
				.param("contacto", idContacto)
				.param("canal", canal.name())
				.param("ronda", ronda)
				.param("ahora", Timestamp.from(ahora))
				.query(UUID.class)
				.optional();
	}

}
