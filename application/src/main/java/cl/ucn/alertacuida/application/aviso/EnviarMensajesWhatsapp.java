package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.error.CanalNoConfigurado;
import cl.ucn.alertacuida.application.aviso.error.EnvioRechazado;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeSalientePendiente;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeSesionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.puerto.CanalWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.ConversacionesWhatsapp;
import java.time.Clock;
import java.time.Instant;

/**
 * Envía los mensajes de sesión (respuestas interactivas, recordatorios y cierres) encolados en
 * {@code mensaje_whatsapp_saliente}. Se ejecuta de forma asíncrona tras el webhook (baja latencia) y también
 * periódicamente como respaldo y para reintentos.
 */
public class EnviarMensajesWhatsapp {

	private static final System.Logger LOG = System.getLogger(EnviarMensajesWhatsapp.class.getName());

	private final ConversacionesWhatsapp conversaciones;
	private final CanalWhatsapp canal;
	private final PoliticaReintentos politica;
	private final Clock reloj;

	/**
	 * @param conversaciones bandeja de mensajes salientes
	 * @param canal          Cloud API de Meta o simulador
	 * @param politica       esperas entre reintentos
	 */
	public EnviarMensajesWhatsapp(ConversacionesWhatsapp conversaciones, CanalWhatsapp canal,
			PoliticaReintentos politica, Clock reloj) {
		this.conversaciones = conversaciones;
		this.canal = canal;
		this.politica = politica;
		this.reloj = reloj;
	}

	/** Envía una vez todos los mensajes de sesión pendientes cuyo próximo envío ya llegó. */
	public void enviar() {
		Instant ahora = reloj.instant();
		for (MensajeSalientePendiente pendiente : conversaciones.mensajesSalientesPendientes(ahora)) {
			try {
				enviarUno(pendiente, ahora);
			} catch (RuntimeException falla) {
				LOG.log(System.Logger.Level.ERROR, "Error al enviar mensaje saliente de WhatsApp "
						+ pendiente.idMensaje(), falla);
			}
		}
	}

	/** Envía un mensaje de sesión y actualiza su estado y ultimo_wamid de la conversación. */
	private void enviarUno(MensajeSalientePendiente pendiente, Instant ahora) {
		if (pendiente.telefono() == null || pendiente.telefono().isBlank()) {
			conversaciones.marcarMensajeSalienteFallido(pendiente.idMensaje(), "Sin teléfono de destino", ahora);
			return;
		}
		String wamid;
		try {
			wamid = canal.enviarSesion(new MensajeSesionWhatsapp(pendiente.telefono(), pendiente.cuerpo(),
					pendiente.botones()));
		} catch (CanalNoConfigurado | EnvioRechazado definitivo) {
			conversaciones.marcarMensajeSalienteFallido(pendiente.idMensaje(), definitivo.getMessage(), ahora);
			return;
		} catch (RuntimeException falla) {
			String motivo = EnviarCorreosPendientes.motivo(falla);
			LOG.log(System.Logger.Level.WARNING, "Falló mensaje saliente de WhatsApp " + pendiente.idMensaje()
					+ ": " + motivo);
			politica.esperaTras(pendiente.reintentos()).ifPresentOrElse(
					espera -> conversaciones.reprogramarMensajeSaliente(pendiente.idMensaje(),
							pendiente.reintentos() + 1, ahora.plus(espera)),
					() -> conversaciones.marcarMensajeSalienteFallido(pendiente.idMensaje(), motivo, ahora));
			return;
		}
		conversaciones.marcarMensajeSalienteEnviado(pendiente.idMensaje(), pendiente.idConversacion(), wamid, ahora);
	}

}
