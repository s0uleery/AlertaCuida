package cl.ucn.alertacuida.adapters.persistencia.alerta;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.alerta.modelo.DestinoDerivacion;
import cl.ucn.alertacuida.application.alerta.modelo.TipoSeguimiento;
import cl.ucn.alertacuida.application.alerta.puerto.Seguimientos;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acciones del equipo sobre un caso (tabla seguimiento), con la nota cifrada, y el catálogo de destinos. */
@Repository
public class SeguimientosJdbc implements Seguimientos {

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado cifra la nota
	 */
	public SeguimientosJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	/** "Cerrada del todo" sale de la vista alerta_estado: atención RESUELTA o contacto CANCELADA. */
	@Override
	public Optional<UUID> registrar(UUID idAlerta, UUID idUsuario, TipoSeguimiento tipo, String destino, String nota,
			Instant ahora) {
		return jdbc.sql("""
				INSERT INTO seguimiento (id_alerta, id_usuario, tipo, codigo_destino, nota, fecha_hora)
				SELECT :alerta, :usuario, :tipo, :destino, :nota, :ahora
				WHERE EXISTS (
					SELECT 1 FROM alerta_estado e WHERE e.id_alerta = :alerta
						AND e.estado_atencion <> 'RESUELTA' AND e.estado_contacto <> 'CANCELADA')
				RETURNING id_seguimiento""")
				.param("alerta", idAlerta)
				.param("usuario", idUsuario)
				.param("tipo", tipo.name())
				.param("destino", destino)
				.param("nota", cifrado.cifrar(nota, ColumnaCifrada.SEGUIMIENTO_NOTA))
				.param("ahora", Timestamp.from(ahora))
				.query(UUID.class)
				.optional();
	}

	@Override
	public boolean destinoActivo(String codigo) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM destino_derivacion WHERE codigo = :c AND activo)")
				.param("c", codigo).query(Boolean.class).single();
	}

	@Override
	public List<DestinoDerivacion> destinosActivos() {
		return jdbc.sql("SELECT codigo, nombre FROM destino_derivacion WHERE activo ORDER BY nombre")
				.query((fila, n) -> new DestinoDerivacion(fila.getString("codigo"), fila.getString("nombre")))
				.list();
	}

}
