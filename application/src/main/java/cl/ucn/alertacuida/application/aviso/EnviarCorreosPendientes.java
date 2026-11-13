package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import java.time.Clock;
import java.time.Instant;

/**
 * Envía los correos de la bandeja de salida a los que les toca. Si un envío falla, lo reprograma según
 * la política de reintentos y, agotados, lo deja fallido con el motivo, a la vista del equipo. Un
 * correo que falla no detiene a los demás.
 */
public class EnviarCorreosPendientes {

	/** Largo máximo del motivo que se guarda, para que un error enorme no llene la tabla. */
	static final int LARGO_MOTIVO = 500;

	private static final System.Logger LOG = System.getLogger(EnviarCorreosPendientes.class.getName());

	private final BandejaSalida bandeja;
	private final CanalNotificacion canal;
	private final PlantillasCorreo plantillas;
	private final PoliticaReintentos politica;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionCorreo.
	 *
	 * @param canal    correo institucional o canal simulado
	 * @param politica esperas entre reintentos
	 */
	public EnviarCorreosPendientes(BandejaSalida bandeja, CanalNotificacion canal, PlantillasCorreo plantillas,
			PoliticaReintentos politica, Clock reloj) {
		this.bandeja = bandeja;
		this.canal = canal;
		this.plantillas = plantillas;
		this.politica = politica;
		this.reloj = reloj;
	}

	/** Envía una vez todos los correos cuyo próximo envío ya llegó. */
	public void enviar() {
		Instant ahora = reloj.instant();
		for (CorreoPendiente correo : bandeja.pendientes(ahora)) {
			try {
				enviarUno(correo, ahora);
			} catch (RuntimeException falla) {
				LOG.log(System.Logger.Level.ERROR, "No se pudo registrar el envío del correo " + correo.idIntento(),
						falla);
			}
		}
	}

	/** Envía un correo y registra el resultado. */
	private void enviarUno(CorreoPendiente correo, Instant ahora) {
		if (correo.destinatario() == null || correo.destinatario().isBlank()) {
			bandeja.marcarFallido(correo.idIntento(), "El destino ya no tiene correo", ahora);
			return;
		}
		String idExterno;
		try {
			idExterno = canal.enviar(plantillas.redactar(correo));
		} catch (RuntimeException falla) {
			reintentarOFallar(correo, motivo(falla), ahora);
			return;
		}
		bandeja.marcarEnviado(correo.idIntento(), idExterno, ahora);
	}

	/** Reprograma el correo si quedan reintentos; si no, lo deja fallido. */
	private void reintentarOFallar(CorreoPendiente correo, String motivo, Instant ahora) {
		LOG.log(System.Logger.Level.WARNING, "Falló el correo " + correo.idIntento() + ": " + motivo);
		politica.esperaTras(correo.reintentos()).ifPresentOrElse(
				espera -> bandeja.reprogramar(correo.idIntento(), correo.reintentos() + 1, ahora.plus(espera)),
				() -> bandeja.marcarFallido(correo.idIntento(), motivo, ahora));
	}

	/** @return el motivo legible de la falla, acotado */
	static String motivo(RuntimeException falla) {
		String texto = falla.getMessage() == null ? falla.getClass().getSimpleName() : falla.getMessage();
		return texto.length() <= LARGO_MOTIVO ? texto : texto.substring(0, LARGO_MOTIVO);
	}

}
