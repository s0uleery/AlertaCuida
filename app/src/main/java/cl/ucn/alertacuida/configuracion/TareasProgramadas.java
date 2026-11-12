package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.aviso.EncolarCorreos;
import cl.ucn.alertacuida.application.aviso.EnviarCorreosPendientes;
import cl.ucn.alertacuida.application.avisoreloj.EnviarAvisosReloj;
import cl.ucn.alertacuida.application.sesion.LimpiarTokensVencidos;
import cl.ucn.alertacuida.application.vigilancia.VigilarRelojes;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Tareas que el backend corre solo, sin que llegue una petición. */
@Configuration
@EnableScheduling
public class TareasProgramadas {

	private final LimpiarTokensVencidos limpiarTokens;
	private final VigilarRelojes vigilarRelojes;
	private final EncolarCorreos encolarCorreos;
	private final EnviarCorreosPendientes enviarCorreos;
	private final EnviarAvisosReloj enviarAvisosReloj;

	/**
	 * @param limpiarTokens     borra los tokens de renovación vencidos
	 * @param vigilarRelojes    aplica las reglas de ausencia a los relojes
	 * @param encolarCorreos    deja en la bandeja de salida los correos que corresponden
	 * @param enviarCorreos     envía los correos pendientes, con reintentos
	 * @param enviarAvisosReloj envía los avisos pendientes al reloj, con reintentos
	 */
	public TareasProgramadas(LimpiarTokensVencidos limpiarTokens, VigilarRelojes vigilarRelojes,
			EncolarCorreos encolarCorreos, EnviarCorreosPendientes enviarCorreos,
			EnviarAvisosReloj enviarAvisosReloj) {
		this.limpiarTokens = limpiarTokens;
		this.vigilarRelojes = vigilarRelojes;
		this.encolarCorreos = encolarCorreos;
		this.enviarCorreos = enviarCorreos;
		this.enviarAvisosReloj = enviarAvisosReloj;
	}

	/** Envía los avisos al reloj cada cierto tiempo; la primera vez, un intervalo después de arrancar. */
	@Scheduled(fixedDelayString = "${alertacuida.fcm.intervalo}",
			initialDelayString = "${alertacuida.fcm.intervalo}")
	void avisarAlReloj() {
		enviarAvisosReloj.enviar();
	}

	/** Revisa los relojes cada cierto tiempo; la primera vez, un intervalo después de arrancar. */
	@Scheduled(fixedDelayString = "${alertacuida.vigilancia.intervalo}",
			initialDelayString = "${alertacuida.vigilancia.intervalo}")
	void vigilarRelojes() {
		vigilarRelojes.vigilar();
	}

	/** Encola y envía los correos cada cierto tiempo; la primera vez, un intervalo después de arrancar. */
	@Scheduled(fixedDelayString = "${alertacuida.correo.intervalo}",
			initialDelayString = "${alertacuida.correo.intervalo}")
	void avisarPorCorreo() {
		encolarCorreos.encolar();
		enviarCorreos.enviar();
	}

	/** Limpia los tokens vencidos una vez al día, a las 04:00 de Chile, cuando casi no hay uso. */
	@Scheduled(cron = "0 0 4 * * *", zone = "America/Santiago")
	void limpiarTokensVencidos() {
		limpiarTokens.limpiar();
	}

}
