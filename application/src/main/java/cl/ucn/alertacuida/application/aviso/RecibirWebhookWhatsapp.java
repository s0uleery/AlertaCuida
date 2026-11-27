package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.modelo.EventoWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.EstadosWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.RespuestaPorIntento;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesa lo que Meta envía al webhook (guía "create webhook endpoint", actualizado el 26-11-2026):
 * <ul>
 * <li>Estados: "delivered" y "read" llenan sus fechas en el aviso; "failed" lo deja fallido con el motivo de
 * Meta, y eso dispara el correo de respaldo.</li>
 * <li>Botones: si existe una conversación registrada para el {@code context.id}, la maneja
 * {@link GestionarConversacionWhatsapp}; si no, aplica el respaldo heredado por {@code registro_intento}.</li>
 * <li>Cada evento se aísla en su propia transacción y los errores de negocio se registran sin devolver 4xx/5xx a
 * Meta (evitando reintentos en bucle y rollbacks del resto del lote).</li>
 * </ul>
 */
public class RecibirWebhookWhatsapp {

	private static final System.Logger LOG = System.getLogger(RecibirWebhookWhatsapp.class.getName());

	private final EstadosWhatsapp estados;
	private final RespuestaPorIntento respuestas;
	private final GestionarConversacionWhatsapp conversaciones;
	private final Runnable alTerminar;
	private final Transacciones transacciones;

	/** Constructor simple sin motor conversacional (pruebas unitarias de estados y botones heredados). */
	public RecibirWebhookWhatsapp(EstadosWhatsapp estados, RespuestaPorIntento respuestas,
			Transacciones transacciones) {
		this(estados, respuestas, null, () -> { }, transacciones);
	}

	/**
	 * @param estados        estados de entrega por wamid
	 * @param respuestas     registro heredado de respuesta simple por intento
	 * @param conversaciones motor conversacional v3 (opcional)
	 * @param alTerminar     dispara el envío asíncrono de respuestas encoladas tras el webhook
	 */
	public RecibirWebhookWhatsapp(EstadosWhatsapp estados, RespuestaPorIntento respuestas,
			GestionarConversacionWhatsapp conversaciones, Runnable alTerminar, Transacciones transacciones) {
		this.estados = estados;
		this.respuestas = respuestas;
		this.conversaciones = conversaciones;
		this.alTerminar = alTerminar == null ? () -> { } : alTerminar;
		this.transacciones = transacciones;
	}

	/**
	 * Procesa cada evento del webhook de forma aislada: un fallo puntual no revierte los demás estados del lote
	 * ni provoca reintentos infinitos de Meta.
	 *
	 * @param eventos todos los de un mismo POST, en orden
	 */
	public void procesar(List<EventoWhatsapp> eventos) {
		boolean huboBoton = false;
		for (EventoWhatsapp evento : eventos) {
			try {
				boolean esBoton = transacciones.ejecutar(() -> {
					switch (evento) {
						case EventoWhatsapp.Estado estado -> {
							registrar(estado);
							return false;
						}
						case EventoWhatsapp.Boton boton -> {
							responder(boton);
							return true;
						}
					}
				});
				huboBoton = huboBoton || esBoton;
			} catch (RuntimeException falla) {
				LOG.log(System.Logger.Level.WARNING, "Evento de webhook de WhatsApp descartado por error: "
						+ falla.getMessage(), falla);
			}
		}
		if (huboBoton) {
			alTerminar.run();
		}
	}

	/** Guarda la fecha que corresponde al estado. */
	private void registrar(EventoWhatsapp.Estado estado) {
		switch (estado.estado()) {
			case "delivered" -> estados.registrarEntrega(estado.idExterno(), estado.fecha());
			case "read" -> estados.registrarLectura(estado.idExterno(), estado.fecha());
			case "failed" -> estados.registrarFallo(estado.idExterno(),
					estado.motivo() == null ? "Meta no pudo entregar el mensaje" : estado.motivo(), estado.fecha());
			default -> {
				// "sent" ya quedó al enviar; "played" es de mensajes de voz
			}
		}
	}

	/** Enruta el botón al motor conversacional o, si no existe conversación, al registro heredado por intento. */
	private void responder(EventoWhatsapp.Boton boton) {
		if (conversaciones != null && conversaciones.procesarBoton(boton)) {
			return;
		}
		Optional<RespuestaContacto> respuesta = respuestaDe(boton.valor());
		Optional<UUID> intento = boton.idMensajeOriginal() == null ? Optional.empty()
				: estados.intentoPorIdExterno(boton.idMensajeOriginal());
		if (respuesta.isEmpty() || intento.isEmpty()) {
			LOG.log(System.Logger.Level.INFO, "Botón de WhatsApp ignorado: no es de un aviso de AlertaCuida");
			return;
		}
		respuestas.responder(intento.get(), respuesta.get());
	}

	/** @return la respuesta heredada de ese payload; vacía si no es uno de los nuestros */
	private static Optional<RespuestaContacto> respuestaDe(String valor) {
		try {
			return Optional.of(RespuestaContacto.valueOf(valor));
		} catch (IllegalArgumentException | NullPointerException ajeno) {
			return Optional.empty();
		}
	}

}
