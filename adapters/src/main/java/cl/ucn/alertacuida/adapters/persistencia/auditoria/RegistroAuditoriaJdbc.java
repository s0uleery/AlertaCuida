package cl.ucn.alertacuida.adapters.persistencia.auditoria;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe en registro_auditoria. Los valores válidos de acción y entidad los controla la base
 * con un CHECK (V1): un valor mal escrito falla al guardar, no queda un registro inservible.
 */
@Repository
public class RegistroAuditoriaJdbc implements RegistroAuditoria {

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	/**
	 * @param jdbc cliente de Spring sobre la conexión a PostgreSQL
	 * @param json convierte el detalle a JSON para la columna jsonb
	 */
	public RegistroAuditoriaJdbc(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/**
	 * @param idUsuario quien hizo la acción
	 * @param idEntidad identificador del objeto afectado, como texto
	 * @param ip        desde dónde llegó la petición
	 * @param detalle   se guarda como JSON; vacío deja la columna en null
	 */
	@Override
	public void registrar(UUID idUsuario, String accion, String entidad, String idEntidad, String ip,
			Instant fechaHora, Map<String, Object> detalle) {
		jdbc.sql("""
				INSERT INTO registro_auditoria (id_usuario, accion, entidad, id_entidad, detalle, fecha_hora, ip)
				VALUES (:usuario, :accion, :entidad, :idEntidad, CAST(:detalle AS jsonb), :fechaHora, :ip)""")
				.param("usuario", idUsuario)
				.param("accion", accion)
				.param("entidad", entidad)
				.param("idEntidad", idEntidad)
				.param("detalle", detalle == null || detalle.isEmpty() ? null : json.writeValueAsString(detalle))
				.param("fechaHora", Timestamp.from(fechaHora))
				.param("ip", ip)
				.update();
	}

}
