package cl.ucn.alertacuida.adapters.persistencia.escalamiento;

import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.application.escalamiento.modelo.AvisoOficina;
import cl.ucn.alertacuida.application.escalamiento.puerto.AvisosOficina;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Avisos a la oficina PRLAC sobre registro_intento. Cada coordinador es un contacto interno (contacto.id_usuario),
 * que se crea la primera vez, igual que en los correos de recibe_avisos. La clave única de los avisos al equipo
 * (alerta, contacto interno, canal y propósito) impide repetirlos; por eso un coordinador con recibe_avisos y sin
 * opt-in recibe un solo correo por alerta técnica.
 */
@Repository
public class AvisosOficinaJdbc implements AvisosOficina {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public AvisosOficinaJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<AvisoOficina> porEncolar(Instant desde) {
		return jdbc.sql("""
				WITH coordinador AS (
					SELECT id_usuario, CASE WHEN telefono IS NOT NULL AND fecha_opt_in IS NOT NULL
						AND fecha_revocacion_opt_in IS NULL THEN 'WHATSAPP' ELSE 'CORREO' END AS canal
					FROM usuario WHERE activo AND codigo_rol = :rol
				), abierta AS (
					SELECT a.id_alerta, a.fecha_hora_recepcion, a.fecha_hora_agravamiento
					FROM alerta a LEFT JOIN atencion_alerta t ON t.id_alerta = a.id_alerta
					WHERE NOT a.es_simulacro AND a.fecha_hora_cancelacion IS NULL AND t.fecha_hora_cierre IS NULL
				), candidato AS (
					SELECT x.id_alerta, c.id_usuario, 'AVISO' AS proposito, c.canal, x.fecha_hora_recepcion AS momento
					FROM abierta x CROSS JOIN coordinador c
					WHERE x.fecha_hora_recepcion >= :desde
					UNION ALL
					SELECT x.id_alerta, c.id_usuario, 'AGRAVAMIENTO', c.canal, x.fecha_hora_agravamiento
					FROM abierta x CROSS JOIN coordinador c
					WHERE x.fecha_hora_agravamiento >= :desde
					AND EXISTS (SELECT 1 FROM registro_intento i
						JOIN contacto ci ON ci.id_contacto = i.id_contacto_interno
						WHERE i.id_alerta = x.id_alerta AND ci.id_usuario = c.id_usuario AND i.proposito = 'AVISO'
						AND i.fecha_hora_solicitud < x.fecha_hora_agravamiento)
				)
				SELECT * FROM candidato v
				WHERE NOT EXISTS (SELECT 1 FROM registro_intento i
					JOIN contacto ci ON ci.id_contacto = i.id_contacto_interno
					WHERE i.id_alerta = v.id_alerta AND ci.id_usuario = v.id_usuario AND i.canal = v.canal
					AND i.proposito = v.proposito)
				ORDER BY momento""")
				.param("rol", EscalamientosTrabadosJdbc.ROL_OFICINA)
				.param("desde", Timestamp.from(desde))
				.query((fila, n) -> new AvisoOficina(fila.getObject("id_alerta", UUID.class),
						fila.getObject("id_usuario", UUID.class), Proposito.valueOf(fila.getString("proposito")),
						Canal.valueOf(fila.getString("canal")), fila.getTimestamp("momento").toInstant()))
				.list();
	}

	@Override
	public void encolar(AvisoOficina aviso, Instant ahora) {
		jdbc.sql("INSERT INTO contacto (id_usuario) VALUES (:usuario) ON CONFLICT (id_usuario) DO NOTHING")
				.param("usuario", aviso.idUsuario())
				.update();
		jdbc.sql("""
				INSERT INTO registro_intento (event_id, id_alerta, id_contacto_interno, canal, proposito,
					fecha_hora_solicitud, fecha_hora_proximo_envio)
				SELECT gen_random_uuid(), :alerta, id_contacto, :canal, :proposito, :ahora, :ahora
				FROM contacto WHERE id_usuario = :usuario
				ON CONFLICT DO NOTHING""")
				.param("alerta", aviso.idAlerta())
				.param("usuario", aviso.idUsuario())
				.param("canal", aviso.canal().name())
				.param("proposito", aviso.proposito().name())
				.param("ahora", Timestamp.from(ahora))
				.update();
	}

}
