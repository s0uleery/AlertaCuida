package cl.ucn.alertacuida.adapters.persistencia.redseguridad;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.redseguridad.modelo.CriterioHallazgos;
import cl.ucn.alertacuida.application.redseguridad.modelo.Hallazgo;
import cl.ucn.alertacuida.application.redseguridad.modelo.Hallazgo.PacienteDeHallazgo;
import cl.ucn.alertacuida.application.redseguridad.puerto.Hallazgos;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Hallazgos de la red de seguridad, en una sola consulta: no se guardan, se calculan (modelo de datos). Para
 * cada lectura del rango busca el umbral y la configuración vigentes al medir (el historial de umbral y
 * configuracion, por vigente_desde) y descarta las que tienen una alerta del reloj dentro de la ventana.
 */
@Repository
public class HallazgosJdbc implements Hallazgos {

	/**
	 * Límites graves vigentes al medir: los del umbral propio si su última fila está completa; si está vacía
	 * o no hay fila, los base (la SpO2 no tiene límite alto). Sin configuración vigente, los módulos de FC y
	 * SpO2 están encendidos, como en la tabla.
	 */
	private static final String CONSULTA = """
			SELECT p.id_paciente, p.nombres, p.apellidos, r.tipo, r.valor, r.fecha_hora_medicion, l.grave_bajo,
				l.grave_alto
			FROM registro_vital r
			JOIN asignacion_reloj a USING (id_asignacion)
			JOIN paciente p USING (id_paciente)
			LEFT JOIN LATERAL (
				SELECT u.grave_bajo, u.grave_alto FROM umbral u
				WHERE u.id_paciente = a.id_paciente AND u.variable = r.tipo
					AND u.vigente_desde <= r.fecha_hora_medicion
				ORDER BY u.vigente_desde DESC LIMIT 1) propio ON true
			CROSS JOIN LATERAL (
				SELECT CASE WHEN propio.grave_bajo IS NOT NULL THEN propio.grave_bajo
						WHEN r.tipo = 'FC' THEN CAST(:fcBajo AS numeric)
						ELSE CAST(:spo2Bajo AS numeric) END AS grave_bajo,
					CASE WHEN propio.grave_bajo IS NOT NULL THEN propio.grave_alto
						WHEN r.tipo = 'FC' THEN CAST(:fcAlto AS numeric) END AS grave_alto) l
			LEFT JOIN LATERAL (
				SELECT c.fc_activa, c.spo2_activa FROM configuracion c
				WHERE c.id_paciente = a.id_paciente AND c.vigente_desde <= r.fecha_hora_medicion
				ORDER BY c.vigente_desde DESC LIMIT 1) modulos ON true
			WHERE r.fecha_hora_medicion >= :desde AND r.fecha_hora_medicion < :hasta
				AND (r.valor < l.grave_bajo OR (l.grave_alto IS NOT NULL AND r.valor > l.grave_alto))
				AND COALESCE(CASE r.tipo WHEN 'FC' THEN modulos.fc_activa ELSE modulos.spo2_activa END, true)
				AND NOT EXISTS (
					SELECT 1 FROM alerta al
					WHERE al.id_asignacion = r.id_asignacion AND al.origen = 'RELOJ' AND NOT al.es_simulacro
						AND al.tipo_evento IN ('FC', 'SPO2', 'FC_SPO2', 'TENDENCIA')
						AND al.fecha_hora_deteccion
							BETWEEN r.fecha_hora_medicion - make_interval(secs => :ventana)
							AND r.fecha_hora_medicion + make_interval(secs => :ventana))
			ORDER BY r.fecha_hora_medicion DESC, r.id_registro
			LIMIT :limite""";

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra los nombres de los pacientes
	 */
	public HallazgosJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public List<Hallazgo> buscar(CriterioHallazgos criterio) {
		return jdbc.sql(CONSULTA)
				.param("fcBajo", criterio.baseFc().graveBajo())
				.param("fcAlto", criterio.baseFc().graveAlto())
				.param("spo2Bajo", criterio.baseSpo2().graveBajo())
				.param("desde", Timestamp.from(criterio.desde()))
				.param("hasta", Timestamp.from(criterio.hasta()))
				.param("ventana", (double) criterio.ventana().toSeconds())
				.param("limite", criterio.limite())
				.query((fila, n) -> hallazgo(fila))
				.list();
	}

	/** Arma el hallazgo con el nombre descifrado y el límite que la lectura superó. */
	private Hallazgo hallazgo(ResultSet fila) throws SQLException {
		BigDecimal valor = fila.getBigDecimal("valor");
		BigDecimal graveBajo = fila.getBigDecimal("grave_bajo");
		BigDecimal limite = valor.compareTo(graveBajo) < 0 ? graveBajo : fila.getBigDecimal("grave_alto");
		PacienteDeHallazgo paciente = new PacienteDeHallazgo(fila.getObject("id_paciente", UUID.class),
				cifrado.descifrar(fila.getString("nombres"), ColumnaCifrada.PACIENTE_NOMBRES),
				cifrado.descifrar(fila.getString("apellidos"), ColumnaCifrada.PACIENTE_APELLIDOS));
		return new Hallazgo(paciente, TipoLectura.valueOf(fila.getString("tipo")), valor.doubleValue(),
				fila.getTimestamp("fecha_hora_medicion").toInstant(), limpio(limite));
	}

	/** @return el límite sin ceros sobrantes ni notación científica (130 y no 130.0) */
	private static BigDecimal limpio(BigDecimal limite) {
		BigDecimal sinCeros = limite.stripTrailingZeros();
		return sinCeros.scale() < 0 ? sinCeros.setScale(0) : sinCeros;
	}

}
