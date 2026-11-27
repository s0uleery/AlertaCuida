package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.aviso.modelo.BotonInteractivo;
import cl.ucn.alertacuida.application.aviso.modelo.ConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.EtapaConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeSalientePendiente;
import cl.ucn.alertacuida.application.aviso.modelo.NivelConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.ResultadoCierreWhatsapp;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia del flujo conversacional de WhatsApp (conversacion_whatsapp, mensaje_whatsapp_saliente y
 * webhook_whatsapp_procesado).
 */
public interface ConversacionesWhatsapp {

	/** Crea la conversación en ESPERANDO_ACEPTACION al enviarse la plantilla inicial. */
	void crear(UUID idIntento, UUID idAlerta, NivelConversacion nivel, String wamid, Instant ahora);

	/**
	 * Deduplica mensajes entrantes por {@code messages[].id}.
	 *
	 * @return true si es la primera vez que llega este id; false si Meta lo reenvió
	 */
	boolean registrarMensajeProcesado(String idMensajeMeta, Instant ahora);

	/**
	 * Busca la conversación correspondiente a un wamid (ya sea el de la plantilla inicial en registro_intento,
	 * el último guardado o cualquiera de los mensajes interactivos enviados en esa conversación).
	 */
	Optional<ConversacionWhatsapp> buscarPorWamid(String wamid);

	/**
	 * Toma atómicamente la alerta para esta conversación (de ESPERANDO_ACEPTACION a {@code nuevaEtapa}).
	 *
	 * @return true si la tomó; false si otro contacto ya la había tomado o ya cambió de etapa
	 */
	boolean tomar(UUID idConversacion, EtapaConversacion nuevaEtapa, Instant proximoVencimiento,
			Instant ventana24h, Instant ahora);

	/**
	 * Avanza de una etapa esperada a la siguiente, refrescando la ventana de 24 h.
	 *
	 * @return true si se aplicó el cambio
	 */
	boolean avanzar(UUID idConversacion, EtapaConversacion etapaEsperada, EtapaConversacion nuevaEtapa,
			Instant proximoVencimiento, Instant ventana24h, Instant ahora);

	/** Refresca la ventana de 24 h cuando el usuario interactúa sin cambiar de etapa (ej. "Aún no llega"). */
	void refrescarVentana(UUID idConversacion, Instant ventana24h, Instant ahora);

	/** Actualiza el contador y la hora del próximo recordatorio de llegada. */
	void registrarRecordatorio(UUID idConversacion, int recordatoriosEnviados, Instant proximoVencimiento,
			Instant ahora);

	/** Cierra la conversación en una etapa terminal y limpia su vencimiento pendiente. */
	void cerrarConversacion(UUID idConversacion, EtapaConversacion etapaFinal, Instant ahora);

	/** Registra la respuesta inicial en registro_intento (primera respuesta). */
	void registrarRespuestaIntento(UUID idIntento, RespuestaContacto respuesta, Instant ahora);

	/** Registra la llegada en registro_intento. */
	void registrarLlegadaIntento(UUID idIntento, Instant ahora);

	/**
	 * Cierra la alerta en modo parcial (fecha_hora_cierre + resultado_cierre_whatsapp, sin resolutor ni
	 * clasificación definitiva, para que un encargado PRLAC la valide desde el dashboard).
	 *
	 * @return true si la alerta estaba abierta y se cerró parcialmente
	 */
	boolean cerrarAlertaParcial(UUID idAlerta, ResultadoCierreWhatsapp resultado, Instant ahora);

	/** Deja en aviso_fcm un aviso CAMBIO_ESTADO_ALERTA para que el reloj consulte GET /alertas/{eventId}. */
	void dejarAvisoCambioEstadoReloj(UUID idAlerta);

	/** Encola un mensaje de sesión (texto o botones interactivos) para enviar fuera de la transacción HTTP. */
	void encolarMensajeSaliente(UUID idConversacion, String cuerpo, List<BotonInteractivo> botones, Instant ahora);

	/** @return conversaciones activas cuyo próximo vencimiento (llamada o recordatorio) ya llegó */
	List<ConversacionWhatsapp> vencidas(Instant ahora);

	/** @return mensajes de sesión pendientes de envío cuyo próximo intento ya llegó */
	List<MensajeSalientePendiente> mensajesSalientesPendientes(Instant ahora);

	/** Registra el wamid enviado de un mensaje saliente y lo guarda como ultimo_wamid de la conversación. */
	void marcarMensajeSalienteEnviado(UUID idMensaje, UUID idConversacion, String wamid, Instant ahora);

	/** Reprograma un mensaje saliente que falló temporalmente. */
	void reprogramarMensajeSaliente(UUID idMensaje, int reintentos, Instant proximo);

	/** Marca un mensaje saliente como fallido definitivamente. */
	void marcarMensajeSalienteFallido(UUID idMensaje, String motivo, Instant ahora);

}
