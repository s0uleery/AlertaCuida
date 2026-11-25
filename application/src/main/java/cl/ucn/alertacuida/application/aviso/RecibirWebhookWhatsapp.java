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
 * Procesa lo que Meta envía al webhook (guía "create webhook endpoint", decisiones del 24-11-2026):
 * <ul>
 * <li>Estados: "delivered" y "read" llenan sus fechas en el aviso; "failed" lo deja fallido con el motivo de
 * Meta, y eso dispara el correo de respaldo. "sent" ya quedó al enviar. Un estado repetido o desordenado no
 * cambia nada.</li>
 * <li>Botones: el aviso se encuentra por el wamid del mensaje que tenía el botón, y la respuesta (su payload)
 * se registra con las mismas reglas del enlace del correo.</li>
 * <li>Lo que no es nuestro (un wamid desconocido, otro payload, otros mensajes) se ignora: Meta solo necesita
 * un 200.</li>
 * </ul>
 */
public class RecibirWebhookWhatsapp {

	private static final System.Logger LOG = System.getLogger(RecibirWebhookWhatsapp.class.getName());

	private final EstadosWhatsapp estados;
	private final RespuestaPorIntento respuestas;
	private final Transacciones transacciones;

	/**
	 * @param estados    estados de entrega por wamid
	 * @param respuestas registro de la respuesta, el mismo del enlace del correo
	 */
	public RecibirWebhookWhatsapp(EstadosWhatsapp estados, RespuestaPorIntento respuestas,
			Transacciones transacciones) {
		this.estados = estados;
		this.respuestas = respuestas;
		this.transacciones = transacciones;
	}

	/**
	 * Procesa los eventos de un webhook en una transacción: si la base falla, no queda nada a medias y Meta lo
	 * reintenta.
	 *
	 * @param eventos todos los de un mismo POST, en orden
	 */
	public void procesar(List<EventoWhatsapp> eventos) {
		transacciones.ejecutar(() -> {
			for (EventoWhatsapp evento : eventos) {
				switch (evento) {
					case EventoWhatsapp.Estado estado -> registrar(estado);
					case EventoWhatsapp.Boton boton -> responder(boton);
				}
			}
			return null;
		});
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

	/** Registra el botón como respuesta, si es nuestro. */
	private void responder(EventoWhatsapp.Boton boton) {
		Optional<RespuestaContacto> respuesta = respuestaDe(boton.valor());
		Optional<UUID> intento = boton.idMensajeOriginal() == null ? Optional.empty()
				: estados.intentoPorIdExterno(boton.idMensajeOriginal());
		if (respuesta.isEmpty() || intento.isEmpty()) {
			LOG.log(System.Logger.Level.INFO, "Botón de WhatsApp ignorado: no es de un aviso de AlertaCuida");
			return;
		}
		respuestas.responder(intento.get(), respuesta.get());
	}

	/** @return la respuesta de ese payload; vacía si no es uno de los nuestros */
	private static Optional<RespuestaContacto> respuestaDe(String valor) {
		try {
			return Optional.of(RespuestaContacto.valueOf(valor));
		} catch (IllegalArgumentException | NullPointerException ajeno) {
			return Optional.empty();
		}
	}

}
