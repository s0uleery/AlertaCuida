package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.error.CanalNoConfigurado;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.CanalWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.RegistroEnvios;
import java.time.Clock;
import java.time.Instant;

/**
 * Envía los WhatsApp de la bandeja de salida a los que les toca. Si un envío falla, lo reprograma según la
 * política de reintentos y, agotados, lo deja fallido; un WhatsApp fallido lo toma {@link EncolarCorreos}
 * para enviar el correo de respaldo. Si el canal no está configurado (antes de tener las credenciales de
 * Meta), falla de inmediato, sin reintentos, para que el respaldo salga enseguida.
 */
public class EnviarWhatsappPendientes {

	private static final System.Logger LOG = System.getLogger(EnviarWhatsappPendientes.class.getName());

	private final BandejaWhatsapp bandeja;
	private final RegistroEnvios envios;
	private final CanalWhatsapp canal;
	private final PoliticaReintentos politica;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionEscalamiento.
	 *
	 * @param envios   donde queda lo que pasó con cada envío
	 * @param canal    Meta, el simulador o sin configurar
	 * @param politica esperas entre reintentos
	 */
	public EnviarWhatsappPendientes(BandejaWhatsapp bandeja, RegistroEnvios envios, CanalWhatsapp canal,
			PoliticaReintentos politica, Clock reloj) {
		this.bandeja = bandeja;
		this.envios = envios;
		this.canal = canal;
		this.politica = politica;
		this.reloj = reloj;
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

	/** Envía un WhatsApp y registra el resultado. */
	private void enviarUno(WhatsappPendiente aviso, Instant ahora) {
		if (aviso.telefono() == null || aviso.telefono().isBlank()) {
			envios.marcarFallido(aviso.idIntento(), "El contacto ya no tiene número de WhatsApp", ahora);
			return;
		}
		String idExterno;
		try {
			idExterno = canal.enviar(aviso);
		} catch (CanalNoConfigurado sinCanal) {
			envios.marcarFallido(aviso.idIntento(), sinCanal.getMessage(), ahora);
			return;
		} catch (RuntimeException falla) {
			reintentarOFallar(aviso, EnviarCorreosPendientes.motivo(falla), ahora);
			return;
		}
		envios.marcarEnviado(aviso.idIntento(), idExterno, ahora);
	}

	/** Reprograma el WhatsApp si quedan reintentos; si no, lo deja fallido. */
	private void reintentarOFallar(WhatsappPendiente aviso, String motivo, Instant ahora) {
		LOG.log(System.Logger.Level.WARNING, "Falló el WhatsApp " + aviso.idIntento() + ": " + motivo);
		politica.esperaTras(aviso.reintentos()).ifPresentOrElse(
				espera -> envios.reprogramar(aviso.idIntento(), aviso.reintentos() + 1, ahora.plus(espera)),
				() -> envios.marcarFallido(aviso.idIntento(), motivo, ahora));
	}

}
