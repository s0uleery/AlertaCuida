package cl.ucn.alertacuida.adapters.persistencia.auditoria;

import cl.ucn.alertacuida.application.auditoria.modelo.RegistroAuditado;
import cl.ucn.alertacuida.application.auditoria.puerto.ConsultaAuditoria;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lee registro_auditoria junto con el nombre de quien hizo la acción. */
@Repository
public class ConsultaAuditoriaJdbc implements ConsultaAuditoria {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public ConsultaAuditoriaJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** LEFT JOIN porque un login fallido con un RUN que no existe no tiene cuenta. */
	@Override
	public List<RegistroAuditado> recientes(int limite, boolean incluirLecturas) {
		return jdbc.sql("""
				SELECT a.id_auditoria, a.fecha_hora, a.accion, a.entidad, a.id_entidad, a.detalle::text detalle,
					a.id_usuario, u.nombre_profesional, a.ip
				FROM registro_auditoria a LEFT JOIN usuario u ON u.id_usuario = a.id_usuario
				WHERE :incluirLecturas OR a.accion <> 'LEER'
				ORDER BY a.fecha_hora DESC, a.id_auditoria
				LIMIT :limite""")
				.param("incluirLecturas", incluirLecturas)
				.param("limite", limite)
				.query((fila, n) -> new RegistroAuditado(fila.getObject("id_auditoria", UUID.class),
						fila.getTimestamp("fecha_hora").toInstant(), fila.getString("accion"),
						fila.getString("entidad"), fila.getString("id_entidad"), fila.getString("detalle"),
						fila.getObject("id_usuario", UUID.class), fila.getString("nombre_profesional"),
						fila.getString("ip")))
				.list();
	}

}
