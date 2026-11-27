package cl.ucn.alertacuida.adapters.persistencia.aviso;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.application.aviso.modelo.UbicacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaWhatsapp;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bandeja de salida de WhatsApp sobre registro_intento (canal WHATSAPP): lee y descifra los datos del destinatario
 * y del paciente necesarios para las plantillas de Meta (incluyendo respaldo de coordenadas GPS: evidencia de la
 * alerta → última ubicación sincronizada → centro de zona segura). Omite reintentos de alertas ya cerradas,
 * canceladas o confirmadas.
 */
@Repository
public class WhatsappJdbc implements BandejaWhatsapp {

	/** Cuántos WhatsApp se toman por pasada, para que una cola grande no bloquee la tarea programada. */
	static final int POR_PASADA = 100;

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;
	private final ObjectMapper json;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado descifra el número y los datos personales del paciente
	 * @param json    lee las coordenadas desde alerta.evidencia
	 */
	public WhatsappJdbc(JdbcClient jdbc, CifradoCampos cifrado, ObjectMapper json) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
		this.json = json;
	}

	@Override
	public List<WhatsappPendiente> pendientes(Instant ahora) {
		LocalDate hoy = ahora.atZone(ZonaHoraria.CHILE).toLocalDate();
		return jdbc.sql("""
				SELECT i.id_intento, i.id_alerta, i.reintentos, i.proposito, a.numero, a.tipo_evento, a.es_simulacro,
					a.evidencia::text AS evidencia, COALESCE(a.fecha_hora_deteccion, a.fecha_hora_recepcion) AS hora_ev,
					e.severidad_actual, i.id_contacto_interno IS NOT NULL AS al_equipo, cp.codigo_tipo_contacto,
					c.whatsapp, u.telefono, p.alias, p.nombres, p.apellidos, p.fecha_nacimiento, p.rut, p.direccion,
					s.numero_sim, ub.latitud AS sync_lat, ub.longitud AS sync_lon,
					zs.latitud AS zona_lat, zs.longitud AS zona_lon
				FROM registro_intento i
				JOIN alerta a ON a.id_alerta = i.id_alerta
				JOIN alerta_estado e ON e.id_alerta = i.id_alerta
				JOIN asignacion_reloj r ON r.id_asignacion = a.id_asignacion
				JOIN smartwatch s ON s.id_dispositivo = r.id_dispositivo
				JOIN paciente p ON p.id_paciente = r.id_paciente
				LEFT JOIN contacto_paciente cp ON cp.id_contacto_paciente = i.id_contacto_paciente
				LEFT JOIN contacto c ON c.id_contacto = cp.id_contacto
				LEFT JOIN contacto ci ON ci.id_contacto = i.id_contacto_interno
				LEFT JOIN usuario u ON u.id_usuario = ci.id_usuario
				LEFT JOIN LATERAL (
					SELECT latitud, longitud FROM ubicacion
					WHERE id_asignacion = a.id_asignacion
					ORDER BY fecha_hora_medicion DESC LIMIT 1) ub ON true
				LEFT JOIN LATERAL (
					SELECT latitud, longitud FROM zona_segura
					WHERE id_paciente = p.id_paciente
					ORDER BY vigente_desde DESC LIMIT 1) zs ON true
				WHERE i.canal = 'WHATSAPP' AND i.fecha_hora_proximo_envio <= :ahora
					AND e.estado_contacto NOT IN ('CONFIRMADA', 'RESUELTA', 'CANCELADA')
				ORDER BY i.fecha_hora_proximo_envio
				LIMIT :limite""")
				.param("ahora", Timestamp.from(ahora))
				.param("limite", POR_PASADA)
				.query((fila, n) -> pendiente(fila, hoy))
				.list();
	}

	/** @return el WhatsApp pendiente de la fila, con datos personales y ubicación resueltos */
	private WhatsappPendiente pendiente(ResultSet fila, LocalDate hoy) throws SQLException {
		boolean alEquipo = fila.getBoolean("al_equipo");
		String guardado = fila.getString(alEquipo ? "telefono" : "whatsapp");
		String telefono = guardado == null ? null
				: cifrado.descifrar(guardado,
						alEquipo ? ColumnaCifrada.USUARIO_TELEFONO : ColumnaCifrada.CONTACTO_WHATSAPP);
		String alias = fila.getString("alias");
		String nombres = descifrarOpcional(fila.getString("nombres"), ColumnaCifrada.PACIENTE_NOMBRES);
		String apellidos = descifrarOpcional(fila.getString("apellidos"), ColumnaCifrada.PACIENTE_APELLIDOS);
		String nombreCompleto = unirNombre(nombres, apellidos);
		Integer edad = calcularEdad(descifrarOpcional(fila.getString("fecha_nacimiento"),
				ColumnaCifrada.PACIENTE_FECHA_NACIMIENTO), hoy);
		String rut = descifrarOpcional(fila.getString("rut"), ColumnaCifrada.PACIENTE_RUT);
		String direccion = descifrarOpcional(fila.getString("direccion"), ColumnaCifrada.PACIENTE_DIRECCION);
		UbicacionWhatsapp ubicacion = resolverUbicacion(fila, alias, direccion);
		return new WhatsappPendiente(fila.getObject("id_intento", UUID.class),
				fila.getObject("id_alerta", UUID.class), telefono, fila.getLong("numero"), alias, nombreCompleto,
				edad, rut, direccion, fila.getString("numero_sim"), TipoEvento.valueOf(fila.getString("tipo_evento")),
				Severidad.valueOf(fila.getString("severidad_actual")), fila.getTimestamp("hora_ev").toInstant(),
				ubicacion, fila.getBoolean("es_simulacro"), fila.getInt("reintentos"), alEquipo,
				fila.getString("codigo_tipo_contacto"), Proposito.valueOf(fila.getString("proposito")));
	}

	/**
	 * Respaldo de coordenadas: 1° GPS de la alerta → 2° última ubicación sincronizada → 3° centro de zona segura.
	 */
	private UbicacionWhatsapp resolverUbicacion(ResultSet fila, String alias, String direccion) throws SQLException {
		String etiqueta = alias == null || alias.isBlank() ? "Ubicación del paciente" : "Ubicación de " + alias.strip();
		double[] deAlerta = coordenadasDeEvidencia(fila.getString("evidencia"));
		if (deAlerta != null) {
			return new UbicacionWhatsapp(deAlerta[0], deAlerta[1], etiqueta, direccion);
		}
		Object syncLat = fila.getObject("sync_lat");
		Object syncLon = fila.getObject("sync_lon");
		if (syncLat != null && syncLon != null) {
			return new UbicacionWhatsapp(((Number) syncLat).doubleValue(), ((Number) syncLon).doubleValue(),
					etiqueta, direccion);
		}
		String zonaLat = descifrarOpcional(fila.getString("zona_lat"), ColumnaCifrada.ZONA_SEGURA_LATITUD);
		String zonaLon = descifrarOpcional(fila.getString("zona_lon"), ColumnaCifrada.ZONA_SEGURA_LONGITUD);
		if (zonaLat != null && zonaLon != null) {
			try {
				return new UbicacionWhatsapp(Double.parseDouble(zonaLat), Double.parseDouble(zonaLon),
						etiqueta, direccion);
			} catch (NumberFormatException ignorada) {
				return null;
			}
		}
		return null;
	}

	/** Extrae latitud y longitud de evidencia.ubicacion; null si no viene o no es válida. */
	private double[] coordenadasDeEvidencia(String evidencia) {
		if (evidencia == null || evidencia.isBlank()) {
			return null;
		}
		try {
			JsonNode ubicacion = json.readTree(evidencia).path("ubicacion");
			JsonNode lat = ubicacion.path("latitud");
			JsonNode lon = ubicacion.path("longitud");
			if (lat.isNumber() && lon.isNumber()) {
				return new double[]{lat.asDouble(), lon.asDouble()};
			}
			return null;
		} catch (JacksonException e) {
			return null;
		}
	}

	/** Descifra si no es null; devuelve null si estaba vacío. */
	private String descifrarOpcional(String valor, ColumnaCifrada columna) {
		return valor == null ? null : cifrado.descifrar(valor, columna);
	}

	/** Une nombres y apellidos omitiendo vacíos. */
	private static String unirNombre(String nombres, String apellidos) {
		String n = nombres == null ? "" : nombres.strip();
		String a = apellidos == null ? "" : apellidos.strip();
		String unido = (n + " " + a).strip();
		return unido.isEmpty() ? null : unido;
	}

	/** Calcula la edad en años completos en hora de Chile; null si falta o no es fecha ISO válida. */
	private static Integer calcularEdad(String nacimientoIso, LocalDate hoy) {
		if (nacimientoIso == null || nacimientoIso.isBlank()) {
			return null;
		}
		try {
			return Math.max(0, Period.between(LocalDate.parse(nacimientoIso.strip()), hoy).getYears());
		} catch (RuntimeException invalida) {
			return null;
		}
	}

}
