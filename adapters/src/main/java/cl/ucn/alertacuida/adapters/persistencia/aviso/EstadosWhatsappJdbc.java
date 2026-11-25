package cl.ucn.alertacuida.adapters.persistencia.aviso;

import cl.ucn.alertacuida.application.aviso.puerto.EstadosWhatsapp;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Estados de entrega de los WhatsApp en registro_intento, buscados por id_externo (el wamid de Meta, único). Las
 * condiciones {@code IS NULL} hacen que un webhook repetido o desordenado no cambie lo ya guardado.
 */
@Repository
public class EstadosWhatsappJdbc implements EstadosWhatsapp {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public EstadosWhatsappJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean registrarEntrega(String idExterno, Instant fecha) {
		return jdbc.sql("""
				UPDATE registro_intento SET fecha_hora_entrega = :fecha
				WHERE id_externo = :id AND canal = 'WHATSAPP' AND fecha_hora_entrega IS NULL""")
				.param("fecha", Timestamp.from(fecha))
				.param("id", idExterno)
				.update() == 1;
	}

	@Override
	public boolean registrarLectura(String idExterno, Instant fecha) {
		return jdbc.sql("""
				UPDATE registro_intento SET fecha_hora_lectura = :fecha,
					fecha_hora_entrega = COALESCE(fecha_hora_entrega, :fecha)
				WHERE id_externo = :id AND canal = 'WHATSAPP' AND fecha_hora_lectura IS NULL""")
				.param("fecha", Timestamp.from(fecha))
				.param("id", idExterno)
				.update() == 1;
	}

	/** Un aviso respondido no se marca fallido: si respondió, le llegó. */
	@Override
	public boolean registrarFallo(String idExterno, String motivo, Instant fecha) {
		return jdbc.sql("""
				UPDATE registro_intento SET fecha_hora_fallo = :fecha, motivo_fallo = :motivo,
					fecha_hora_proximo_envio = NULL
				WHERE id_externo = :id AND canal = 'WHATSAPP' AND fecha_hora_fallo IS NULL AND respuesta IS NULL""")
				.param("fecha", Timestamp.from(fecha))
				.param("motivo", motivo)
				.param("id", idExterno)
				.update() == 1;
	}

	@Override
	public Optional<UUID> intentoPorIdExterno(String idExterno) {
		return jdbc.sql("SELECT id_intento FROM registro_intento WHERE id_externo = :id AND canal = 'WHATSAPP'")
				.param("id", idExterno)
				.query(UUID.class)
				.optional();
	}

}
