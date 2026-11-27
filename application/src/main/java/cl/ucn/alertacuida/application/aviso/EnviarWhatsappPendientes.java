package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.error.CanalNoConfigurado;
import cl.ucn.alertacuida.application.aviso.error.EnvioRechazado;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.CanalWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.ConversacionesWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.RegistroEnvios;
import java.time.Clock;
import java.time.Instant;

/**
 * Envía los WhatsApp de la bandeja de salida a los que les toca. Si un envío falla, lo reprograma según la
 * política de reintentos y, agotados, lo deja fallido; un WhatsApp fallido lo toma {@link EncolarCorreos}
 * para enviar el correo de respaldo. Al enviarse con éxito, abre la conversación en {@code conversacion_whatsapp}.
 */
public class EnviarWhatsappPendientes {

	private static final System.Logger LOG = System.getLogger(EnviarWhatsappPendientes.class.getName());

	private final BandejaWhatsapp bandeja;
	private final RegistroEnvios envios;
	private final CanalWhatsapp canal;
	private final PoliticaReintentos politica;
	private final Clock reloj;
	private final ConversacionesWhatsapp conversaciones;

	/** Constructor sin registro de conversación (pruebas unitarias simples). */
	public EnviarWhatsappPendientes(BandejaWhatsapp bandeja, RegistroEnvios envios, CanalWhatsapp canal,
			PoliticaReintentos politica, Clock reloj) {
		this(bandeja, envios, canal, politica, reloj, null);
	}

	/**
	 * Recibe lo que usa; lo arma ConfiguracionEscalamiento.
	 *
	 * @param envios         donde queda lo que pasó con cada envío
	 * @param canal          Meta, el simulador o sin configurar
	 * @param politica       esperas entre reintentos
	 * @param conversaciones opcional; abre la conversación inicial al enviar con éxito
	 */
	public EnviarWhatsappPendientes(BandejaWhatsapp bandeja, RegistroEnvios envios, CanalWhatsapp canal,
			PoliticaReintentos politica, Clock reloj, ConversacionesWhatsapp conversaciones) {
		this.bandeja = bandeja;
		this.envios = envios;
		this.canal = canal;
		this.politica = politica;
		this.reloj = reloj;
		this.conversaciones = conversaciones;
	}

	/** Envía una vez todos los WhatsApp cuyo próximo envío ya llegó. Uno que falla no detiene a los demás. */
	public void enviar() {
		Instant ahora = reloj.instant();
		for (WhatsappPendiente aviso : bandeja.pendientes(ahora)) {
			try {
				enviarUno(aviso, ahora);
			} catch (RuntimeException falla) {
				LOG.log(System.Logger.Level.ERROR, "No se pudo registrar el envío del WhatsApp " + aviso.idIntento(),
						falla);
			}
		}
	}

	/** Envía un WhatsApp y registra el resultado y su conversación inicial. */
	private void enviarUno(WhatsappPendiente aviso, Instant ahora) {
		if (aviso.telefono() == null || aviso.telefono().isBlank()) {
			envios.marcarFallido(aviso.idIntento(), "El contacto ya no tiene número de WhatsApp", ahora);
			return;
		}
		String idExterno;
		try {
			idExterno = canal.enviar(aviso);
		} catch (CanalNoConfigurado | EnvioRechazado definitivo) {
			// Reintentar daría lo mismo: falla al tiro y sale el correo de respaldo
			envios.marcarFallido(aviso.idIntento(), definitivo.getMessage(), ahora);
			return;
		} catch (RuntimeException falla) {
			reintentarOFallar(aviso, EnviarCorreosPendientes.motivo(falla), ahora);
			return;
		}
		envios.marcarEnviado(aviso.idIntento(), idExterno, ahora);
		if (conversaciones != null && aviso.idAlerta() != null) {
			conversaciones.crear(aviso.idIntento(), aviso.idAlerta(), aviso.nivel(), idExterno, ahora);
		}
	}

	/** Reprograma el WhatsApp si quedan reintentos; si no, lo deja fallido. */
	private void reintentarOFallar(WhatsappPendiente aviso, String motivo, Instant ahora) {
		LOG.log(System.Logger.Level.WARNING, "Falló el WhatsApp " + aviso.idIntento() + ": " + motivo);
		politica.esperaTras(aviso.reintentos()).ifPresentOrElse(
				espera -> envios.reprogramar(aviso.idIntento(), aviso.reintentos() + 1, ahora.plus(espera)),
				() -> envios.marcarFallido(aviso.idIntento(), motivo, ahora));
	}

}
