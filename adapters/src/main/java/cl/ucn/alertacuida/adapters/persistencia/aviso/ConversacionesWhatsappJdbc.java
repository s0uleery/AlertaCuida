package cl.ucn.alertacuida.adapters.persistencia.aviso;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.aviso.modelo.BotonInteractivo;
import cl.ucn.alertacuida.application.aviso.modelo.ConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.EtapaConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeSalientePendiente;
import cl.ucn.alertacuida.application.aviso.modelo.NivelConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.ResultadoCierreWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.ConversacionesWhatsapp;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Persistencia JDBC del motor conversacional de WhatsApp (V21): conversaciones, bandeja de mensajes salientes
 * de sesión y deduplicación de webhooks entrantes.
 */
@Repository
public class ConversacionesWhatsappJdbc implements ConversacionesWhatsapp {

	private static final int POR_PASADA = 100;

	private static final String SELECT_CONVERSACION = """
			SELECT cw.id_conversacion, cw.id_intento, cw.id_alerta, a.numero, p.alias, s.numero_sim,
				e.severidad_actual, e.estado_contacto, a.origen = 'RELOJ' AS del_reloj, cw.nivel, cw.etapa,
				cw.ultimo_wamid, cw.recordatorios_enviados, cw.fecha_hora_proximo_vencimiento, cw.ventana_24h_hasta
			FROM conversacion_whatsapp cw
			JOIN alerta a ON a.id_alerta = cw.id_alerta
			JOIN alerta_estado e ON e.id_alerta = cw.id_alerta
			JOIN asignacion_reloj r ON r.id_asignacion = a.id_asignacion
			JOIN smartwatch s ON s.id_dispositivo = r.id_dispositivo
			JOIN paciente p ON p.id_paciente = r.id_paciente
			""";

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;
	private final ObjectMapper json;

	/**
	 * @param jdbc    cliente JDBC
	 * @param cifrado descifra el número destino de los mensajes salientes
	 * @param json    serializa y lee la lista de botones interactivos en jsonb
	 */
	public ConversacionesWhatsappJdbc(JdbcClient jdbc, CifradoCampos cifrado, ObjectMapper json) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
		this.json = json;
	}

	@Override
	public void crear(UUID idIntento, UUID idAlerta, NivelConversacion nivel, String wamid, Instant ahora) {
		jdbc.sql("""
				INSERT INTO conversacion_whatsapp (id_intento, id_alerta, nivel, etapa, ultimo_wamid,
					fecha_hora_creacion, fecha_hora_actualizacion)
				VALUES (:intento, :alerta, :nivel, 'ESPERANDO_ACEPTACION', :wamid, :ahora, :ahora)
				ON CONFLICT (id_intento) DO UPDATE SET ultimo_wamid = EXCLUDED.ultimo_wamid,
					fecha_hora_actualizacion = EXCLUDED.fecha_hora_actualizacion""")
				.param("intento", idIntento)
				.param("alerta", idAlerta)
				.param("nivel", nivel.name())
				.param("wamid", wamid)
				.param("ahora", Timestamp.from(ahora))
				.update();
	}

	@Override
	public boolean registrarMensajeProcesado(String idMensajeMeta, Instant ahora) {
		return jdbc.sql("""
				INSERT INTO webhook_whatsapp_procesado (id_mensaje_meta, fecha_hora_recepcion)
				VALUES (:id, :ahora)
				ON CONFLICT (id_mensaje_meta) DO NOTHING""")
				.param("id", idMensajeMeta)
				.param("ahora", Timestamp.from(ahora))
				.update() == 1;
	}

	@Override
	public Optional<ConversacionWhatsapp> buscarPorWamid(String wamid) {
		return jdbc.sql(SELECT_CONVERSACION + """
				LEFT JOIN registro_intento i ON i.id_intento = cw.id_intento
				WHERE cw.ultimo_wamid = :wamid OR i.id_externo = :wamid
					OR EXISTS (SELECT 1 FROM mensaje_whatsapp_saliente m
						WHERE m.id_conversacion = cw.id_conversacion AND m.id_externo = :wamid)
				LIMIT 1""")
				.param("wamid", wamid)
				.query((fila, n) -> conversacion(fila))
				.optional();
	}

	/**
	 * El índice único parcial {@code conversacion_whatsapp_activa_por_alerta} impide que dos contactos tomen la
	 * misma alerta a la vez: si hay carrera, el segundo recibe DataIntegrityViolationException y devolvemos false.
	 */
	@Override
	public boolean tomar(UUID idConversacion, EtapaConversacion nuevaEtapa, Instant proximoVencimiento,
			Instant ventana24h, Instant ahora) {
		try {
			return jdbc.sql("""
					UPDATE conversacion_whatsapp SET etapa = :nueva, fecha_hora_proximo_vencimiento = :vencimiento,
						ventana_24h_hasta = :ventana, fecha_hora_actualizacion = :ahora
					WHERE id_conversacion = :id AND etapa = 'ESPERANDO_ACEPTACION'
						AND NOT EXISTS (SELECT 1 FROM conversacion_whatsapp otra
							WHERE otra.id_alerta = conversacion_whatsapp.id_alerta
							AND otra.id_conversacion <> conversacion_whatsapp.id_conversacion
							AND otra.etapa IN ('ESPERANDO_ESTADO_LLAMADA', 'ESPERANDO_DISPONIBILIDAD',
								'ESPERANDO_LLEGADA', 'ESPERANDO_RESULTADO_VISITA'))""")
					.param("nueva", nuevaEtapa.name())
					.param("vencimiento", marca(proximoVencimiento))
					.param("ventana", marca(ventana24h))
					.param("ahora", Timestamp.from(ahora))
					.param("id", idConversacion)
					.update() == 1;
		} catch (DataIntegrityViolationException carrera) {
			return false;
		}
	}

	@Override
	public boolean avanzar(UUID idConversacion, EtapaConversacion etapaEsperada, EtapaConversacion nuevaEtapa,
			Instant proximoVencimiento, Instant ventana24h, Instant ahora) {
		return jdbc.sql("""
				UPDATE conversacion_whatsapp SET etapa = :nueva, fecha_hora_proximo_vencimiento = :vencimiento,
					ventana_24h_hasta = :ventana, fecha_hora_actualizacion = :ahora
				WHERE id_conversacion = :id AND etapa = :esperada""")
				.param("nueva", nuevaEtapa.name())
				.param("vencimiento", marca(proximoVencimiento))
				.param("ventana", marca(ventana24h))
				.param("ahora", Timestamp.from(ahora))
				.param("id", idConversacion)
				.param("esperada", etapaEsperada.name())
				.update() == 1;
	}

	@Override
	public void refrescarVentana(UUID idConversacion, Instant ventana24h, Instant ahora) {
		jdbc.sql("""
				UPDATE conversacion_whatsapp SET ventana_24h_hasta = :ventana, fecha_hora_actualizacion = :ahora
				WHERE id_conversacion = :id""")
				.param("ventana", marca(ventana24h))
				.param("ahora", Timestamp.from(ahora))
				.param("id", idConversacion)
				.update();
	}

	@Override
	public void registrarRecordatorio(UUID idConversacion, int recordatoriosEnviados, Instant proximoVencimiento,
			Instant ahora) {
		jdbc.sql("""
				UPDATE conversacion_whatsapp SET recordatorios_enviados = :n,
					fecha_hora_proximo_vencimiento = :vencimiento, fecha_hora_actualizacion = :ahora
				WHERE id_conversacion = :id AND etapa = 'ESPERANDO_LLEGADA'""")
				.param("n", recordatoriosEnviados)
				.param("vencimiento", marca(proximoVencimiento))
				.param("ahora", Timestamp.from(ahora))
				.param("id", idConversacion)
				.update();
	}

	@Override
	public void cerrarConversacion(UUID idConversacion, EtapaConversacion etapaFinal, Instant ahora) {
		jdbc.sql("""
				UPDATE conversacion_whatsapp SET etapa = :etapa, fecha_hora_proximo_vencimiento = NULL,
					fecha_hora_actualizacion = :ahora
				WHERE id_conversacion = :id""")
				.param("etapa", etapaFinal.name())
				.param("ahora", Timestamp.from(ahora))
				.param("id", idConversacion)
				.update();
	}

	@Override
	public void registrarRespuestaIntento(UUID idIntento, RespuestaContacto respuesta, Instant ahora) {
		jdbc.sql("""
				UPDATE registro_intento SET respuesta = :respuesta, fecha_hora_respuesta = :ahora
				WHERE id_intento = :id AND respuesta IS NULL""")
				.param("respuesta", respuesta.name())
				.param("ahora", Timestamp.from(ahora))
				.param("id", idIntento)
				.update();
	}

	@Override
	public void registrarLlegadaIntento(UUID idIntento, Instant ahora) {
		jdbc.sql("""
				UPDATE registro_intento SET fecha_hora_llegada = :ahora
				WHERE id_intento = :id AND fecha_hora_llegada IS NULL AND respuesta = 'VOY_EN_CAMINO'
					AND :ahora >= fecha_hora_respuesta""")
				.param("ahora", Timestamp.from(ahora))
				.param("id", idIntento)
				.update();
	}

	@Override
	public boolean cerrarAlertaParcial(UUID idAlerta, ResultadoCierreWhatsapp resultado, Instant ahora) {
		return jdbc.sql("""
				INSERT INTO atencion_alerta (id_alerta, fecha_hora_cierre, resultado_cierre_whatsapp)
				VALUES (:id, :ahora, :resultado)
				ON CONFLICT (id_alerta) DO UPDATE SET
					fecha_hora_cierre = COALESCE(atencion_alerta.fecha_hora_cierre, EXCLUDED.fecha_hora_cierre),
					resultado_cierre_whatsapp = COALESCE(atencion_alerta.resultado_cierre_whatsapp,
						EXCLUDED.resultado_cierre_whatsapp)
				WHERE atencion_alerta.fecha_hora_cierre IS NULL""")
				.param("id", idAlerta)
				.param("ahora", Timestamp.from(ahora))
				.param("resultado", resultado.name())
				.update() == 1;
	}

	@Override
	public void dejarAvisoCambioEstadoReloj(UUID idAlerta) {
		jdbc.sql("INSERT INTO aviso_fcm (id_alerta, tipo) VALUES (:id, 'CAMBIO_ESTADO_ALERTA')")
				.param("id", idAlerta)
				.update();
	}

	@Override
	public void encolarMensajeSaliente(UUID idConversacion, String cuerpo, List<BotonInteractivo> botones,
			Instant ahora) {
		boolean tieneBotones = botones != null && !botones.isEmpty();
		String botonesJson = tieneBotones ? serializarBotones(botones) : null;
		jdbc.sql("""
				INSERT INTO mensaje_whatsapp_saliente (id_conversacion, tipo, cuerpo, botones,
					fecha_hora_proximo_envio)
				VALUES (:conv, :tipo, :cuerpo, CAST(:botones AS jsonb), :ahora)""")
				.param("conv", idConversacion)
				.param("tipo", tieneBotones ? "INTERACTIVO_BOTONES" : "TEXTO")
				.param("cuerpo", cuerpo)
				.param("botones", botonesJson)
				.param("ahora", Timestamp.from(ahora))
				.update();
	}

	@Override
	public List<ConversacionWhatsapp> vencidas(Instant ahora) {
		return jdbc.sql(SELECT_CONVERSACION + """
				WHERE cw.etapa IN ('ESPERANDO_ESTADO_LLAMADA', 'ESPERANDO_DISPONIBILIDAD', 'ESPERANDO_LLEGADA')
					AND cw.fecha_hora_proximo_vencimiento <= :ahora
				ORDER BY cw.fecha_hora_proximo_vencimiento
				LIMIT :limite""")
				.param("ahora", Timestamp.from(ahora))
				.param("limite", POR_PASADA)
				.query((fila, n) -> conversacion(fila))
				.list();
	}

	@Override
	public List<MensajeSalientePendiente> mensajesSalientesPendientes(Instant ahora) {
		return jdbc.sql("""
				SELECT m.id_mensaje, m.id_conversacion, m.cuerpo, m.botones::text AS botones, m.reintentos,
					i.id_contacto_interno IS NOT NULL AS al_equipo, c.whatsapp, u.telefono
				FROM mensaje_whatsapp_saliente m
				JOIN conversacion_whatsapp cw ON cw.id_conversacion = m.id_conversacion
				JOIN registro_intento i ON i.id_intento = cw.id_intento
				LEFT JOIN contacto_paciente cp ON cp.id_contacto_paciente = i.id_contacto_paciente
				LEFT JOIN contacto c ON c.id_contacto = cp.id_contacto
				LEFT JOIN contacto ci ON ci.id_contacto = i.id_contacto_interno
				LEFT JOIN usuario u ON u.id_usuario = ci.id_usuario
				WHERE m.fecha_hora_proximo_envio <= :ahora
				ORDER BY m.fecha_hora_proximo_envio
				LIMIT :limite""")
				.param("ahora", Timestamp.from(ahora))
				.param("limite", POR_PASADA)
				.query((fila, n) -> mensajeSaliente(fila))
				.list();
	}

	@Override
	public void marcarMensajeSalienteEnviado(UUID idMensaje, UUID idConversacion, String wamid, Instant ahora) {
		jdbc.sql("""
				UPDATE mensaje_whatsapp_saliente SET fecha_hora_envio = :ahora, id_externo = :wamid,
					fecha_hora_proximo_envio = NULL
				WHERE id_mensaje = :id""")
				.param("ahora", Timestamp.from(ahora))
				.param("wamid", wamid)
				.param("id", idMensaje)
				.update();
		jdbc.sql("""
				UPDATE conversacion_whatsapp SET ultimo_wamid = :wamid, fecha_hora_actualizacion = :ahora
				WHERE id_conversacion = :conv""")
				.param("wamid", wamid)
				.param("ahora", Timestamp.from(ahora))
				.param("conv", idConversacion)
				.update();
	}

	@Override
	public void reprogramarMensajeSaliente(UUID idMensaje, int reintentos, Instant proximo) {
		jdbc.sql("""
				UPDATE mensaje_whatsapp_saliente SET reintentos = :reintentos, fecha_hora_proximo_envio = :proximo
				WHERE id_mensaje = :id""")
				.param("reintentos", reintentos)
				.param("proximo", Timestamp.from(proximo))
				.param("id", idMensaje)
				.update();
	}

	@Override
	public void marcarMensajeSalienteFallido(UUID idMensaje, String motivo, Instant ahora) {
		jdbc.sql("""
				UPDATE mensaje_whatsapp_saliente SET fecha_hora_fallo = :ahora, motivo_fallo = :motivo,
					fecha_hora_proximo_envio = NULL
				WHERE id_mensaje = :id""")
				.param("ahora", Timestamp.from(ahora))
				.param("motivo", motivo)
				.param("id", idMensaje)
				.update();
	}

	/** Convierte una fila de conversacion_whatsapp + alerta a modelo de aplicación. */
	private static ConversacionWhatsapp conversacion(ResultSet fila) throws SQLException {
		return new ConversacionWhatsapp(fila.getObject("id_conversacion", UUID.class),
				fila.getObject("id_intento", UUID.class), fila.getObject("id_alerta", UUID.class),
				fila.getLong("numero"), fila.getString("alias"), fila.getString("numero_sim"),
				Severidad.valueOf(fila.getString("severidad_actual")),
				EstadoContacto.valueOf(fila.getString("estado_contacto")), fila.getBoolean("del_reloj"),
				NivelConversacion.valueOf(fila.getString("nivel")),
				EtapaConversacion.valueOf(fila.getString("etapa")), fila.getString("ultimo_wamid"),
				fila.getInt("recordatorios_enviados"), instante(fila, "fecha_hora_proximo_vencimiento"),
				instante(fila, "ventana_24h_hasta"));
	}

	/** Convierte una fila de mensaje_whatsapp_saliente, descifrando el teléfono destino. */
	private MensajeSalientePendiente mensajeSaliente(ResultSet fila) throws SQLException {
		boolean alEquipo = fila.getBoolean("al_equipo");
		String guardado = fila.getString(alEquipo ? "telefono" : "whatsapp");
		String telefono = guardado == null ? null
				: cifrado.descifrar(guardado,
						alEquipo ? ColumnaCifrada.USUARIO_TELEFONO : ColumnaCifrada.CONTACTO_WHATSAPP);
		return new MensajeSalientePendiente(fila.getObject("id_mensaje", UUID.class),
				fila.getObject("id_conversacion", UUID.class), telefono, fila.getString("cuerpo"),
				leerBotones(fila.getString("botones")), fila.getInt("reintentos"));
	}

	/** Serializa botones interactivos a JSON para la columna jsonb. */
	private String serializarBotones(List<BotonInteractivo> botones) {
		List<Map<String, String>> lista = botones.stream()
				.map(b -> Map.of("id", b.id(), "titulo", b.titulo()))
				.toList();
		return json.writeValueAsString(lista);
	}

	/** Deserializa la lista de botones interactivos desde jsonb. */
	private List<BotonInteractivo> leerBotones(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		List<BotonInteractivo> resultado = new ArrayList<>();
		for (JsonNode nodo : json.readTree(raw)) {
			resultado.add(new BotonInteractivo(nodo.path("id").asString(), nodo.path("titulo").asString()));
		}
		return resultado;
	}

	/** @return Timestamp para JDBC o null */
	private static Timestamp marca(Instant instante) {
		return instante == null ? null : Timestamp.from(instante);
	}

	/** @return Instant desde la columna, o null */
	private static Instant instante(ResultSet fila, String columna) throws SQLException {
		Timestamp valor = fila.getTimestamp(columna);
		return valor == null ? null : valor.toInstant();
	}

}
