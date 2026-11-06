package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.alerta.modelo.LimiteAlertas;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límite de alertas de un reloj (alertacuida.alertas.* en application.properties). Pasarlo no
 * rechaza ninguna alerta: crea una alerta técnica EXCESO_ALERTAS para que el equipo revise el reloj.
 *
 * @param avisarDesde    con esta cantidad de alertas en la ventana (contando la que llega) se avisa
 * @param ventanaMinutos   minutos hacia atrás en que se cuentan
 */
@Validated
@ConfigurationProperties("alertacuida.alertas")
public record PropiedadesAlertas(@Min(1) int avisarDesde, @Min(1) @Max(1440) int ventanaMinutos) {

	/** @return el límite para el caso de uso */
	LimiteAlertas limite() {
		return new LimiteAlertas(avisarDesde, Duration.ofMinutes(ventanaMinutos));
	}

}
