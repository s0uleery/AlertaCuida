package cl.ucn.alertacuida.adapters.persistencia.panel;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.panel.modelo.FilaPanel;
import cl.ucn.alertacuida.application.panel.modelo.TotalesAlertas;
import cl.ucn.alertacuida.application.panel.puerto.PanelUsuarios;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Usuarios activos y totales de alertas para la portada. "Abierta" se calcula con la vista alerta_estado, igual
 * que la lista de alertas: sin cerrar por el equipo (en seguimiento cuenta), sin canceladas ni simulacros.
 */
@Repository
public class PanelUsuariosJdbc implements PanelUsuarios {

	/** Alertas abiertas, con su paciente por el vínculo de la alerta (también de relojes ya reemplazados). */
	private static final String ABIERTAS = """
			SELECT v.id_paciente, e.estado_contacto, e.estado_atencion
			FROM alerta a
			JOIN alerta_estado e USING (id_alerta)
			JOIN asignacion_reloj v USING (id_asignacion)
			WHERE NOT a.es_simulacro AND e.estado_contacto <> 'CANCELADA' AND e.estado_atencion <> 'RESUELTA'""";

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra los nombres de los pacientes
	 */
	public PanelUsuariosJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public List<FilaPanel> usuariosActivos() {
		return jdbc.sql("""
				WITH abiertas AS (""" + ABIERTAS + """
				)
				SELECT p.id_paciente, p.nombres, p.apellidos, s.id_dispositivo, s.identificador_reloj,
					(SELECT count(*) FROM abiertas ab WHERE ab.id_paciente = p.id_paciente) AS alertas
				FROM paciente p
				LEFT JOIN asignacion_reloj v ON v.id_paciente = p.id_paciente AND v.fecha_fin IS NULL
				LEFT JOIN smartwatch s ON s.id_dispositivo = v.id_dispositivo
				WHERE p.fecha_baja IS NULL""")
				.query((fila, n) -> new FilaPanel(fila.getObject("id_paciente", UUID.class),
						cifrado.descifrar(fila.getString("nombres"), ColumnaCifrada.PACIENTE_NOMBRES),
						cifrado.descifrar(fila.getString("apellidos"), ColumnaCifrada.PACIENTE_APELLIDOS),
						fila.getObject("id_dispositivo", UUID.class), fila.getString("identificador_reloj"),
						fila.getInt("alertas")))
				.list();
	}

	@Override
	public TotalesAlertas totalesAlertas() {
		return jdbc.sql("""
				WITH abiertas AS (""" + ABIERTAS + """
				)
				SELECT count(*) AS abiertas,
					count(*) FILTER (WHERE estado_contacto = 'SIN_RESPUESTA') AS sin_respuesta,
					count(*) FILTER (WHERE estado_atencion = 'EN_SEGUIMIENTO') AS en_seguimiento
				FROM abiertas""")
				.query((fila, n) -> new TotalesAlertas(fila.getInt("abiertas"), fila.getInt("sin_respuesta"),
						fila.getInt("en_seguimiento")))
				.single();
	}

}
