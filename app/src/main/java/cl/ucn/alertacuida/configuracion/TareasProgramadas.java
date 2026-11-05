package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.sesion.LimpiarTokensVencidos;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Tareas que el backend corre solo, sin que llegue una petición. */
@Configuration
@EnableScheduling
public class TareasProgramadas {

	private final LimpiarTokensVencidos limpiarTokens;

	/** @param limpiarTokens borra los tokens de renovación vencidos */
	public TareasProgramadas(LimpiarTokensVencidos limpiarTokens) {
		this.limpiarTokens = limpiarTokens;
	}

	/** Limpia los tokens vencidos una vez al día, a las 04:00 de Chile, cuando casi no hay uso. */
	@Scheduled(cron = "0 0 4 * * *", zone = "America/Santiago")
	void limpiarTokensVencidos() {
		limpiarTokens.limpiar();
	}

}
