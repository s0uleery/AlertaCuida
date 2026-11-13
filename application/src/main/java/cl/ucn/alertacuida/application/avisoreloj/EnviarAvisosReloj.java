package cl.ucn.alertacuida.application.avisoreloj;

import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.avisoreloj.error.TokenRelojRechazado;
import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoPendiente;
import cl.ucn.alertacuida.application.avisoreloj.puerto.BandejaAvisosReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.CanalAvisoReloj;
import java.time.Clock;
import java.time.Instant;

/**
 * Envía los avisos pendientes al reloj y los reintenta mientras no los acuse. La espera se cuenta desde
 * el envío anterior; agotados los reintentos, el aviso queda a la vista en la línea de tiempo.
 *
 * <p>Un envío que Firebase no aceptó no cuenta como intento, porque el reloj nunca lo pudo recibir: se
 * vuelve a probar en la siguiente pasada. Si rechaza el token, el reloj queda sin token y el aviso espera
 * a que registre uno nuevo. Un aviso que falla no detiene a los demás.
 */
public class EnviarAvisosReloj {

	private static final System.Logger LOG = System.getLogger(EnviarAvisosReloj.class.getName());

	private final BandejaAvisosReloj bandeja;
	private final CanalAvisoReloj canal;
	private final PoliticaReintentos politica;
	private final Clock reloj;

	/**
	 * @param canal    Firebase o canal simulado
	 * @param politica esperas entre un envío sin acuse y el siguiente
	 */
	public EnviarAvisosReloj(BandejaAvisosReloj bandeja, CanalAvisoReloj canal, PoliticaReintentos politica,
			Clock reloj) {
		this.bandeja = bandeja;
		this.canal = canal;
		this.politica = politica;
		this.reloj = reloj;
	}

	/** Envía una vez los avisos a los que les toca. */
	public void enviar() {
		Instant ahora = reloj.instant();
		for (AvisoPendiente aviso : bandeja.sinAcusar(politica.esperas().size() + 1)) {
			if (leToca(aviso, ahora)) {
				enviarUno(aviso, ahora);
			}
		}
	}

	/** @return si nunca se envió, o si ya pasó la espera desde el envío anterior (a la hora justa ya toca) */
	private boolean leToca(AvisoPendiente aviso, Instant ahora) {
		if (aviso.envio() == null) {
			return true;
		}
		return politica.esperaTras(aviso.intentos() - 1)
				.map(espera -> !ahora.isBefore(aviso.envio().plus(espera)))
				.orElse(false);
	}

	/** Envía un aviso y registra el resultado; una falla queda en el log y no afecta a los demás. */
	private void enviarUno(AvisoPendiente aviso, Instant ahora) {
		try {
			canal.enviar(aviso.aviso());
			bandeja.marcarEnviado(aviso.idAviso(), ahora);
		} catch (TokenRelojRechazado rechazo) {
			LOG.log(System.Logger.Level.WARNING, "Firebase rechazó el token del reloj " + aviso.idDispositivo());
			bandeja.olvidarToken(aviso.idDispositivo(), aviso.token());
		} catch (RuntimeException falla) {
			LOG.log(System.Logger.Level.WARNING, "No se pudo enviar el aviso " + aviso.idAviso(), falla);
		}
	}

}
