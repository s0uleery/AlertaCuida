package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.reporte.ConsultarReportes;
import cl.ucn.alertacuida.application.reporte.puerto.Reportes;
import cl.ucn.alertacuida.application.tendencia.ConsultarTendencias;
import cl.ucn.alertacuida.application.tendencia.puerto.Tendencias;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Une los casos de uso de la sección Reportes (tendencias y reportes por usuario) con sus adaptadores. */
@Configuration
public class ConfiguracionReportes {

	/** Evolución de FC y SpO2 de un usuario, para los gráficos. */
	@Bean
	ConsultarTendencias consultarTendencias(Tendencias tendencias, Clock reloj) {
		return new ConsultarTendencias(tendencias, reloj);
	}

	/** Reportes diarios y semanales por usuario, calculados al pedirlos. */
	@Bean
	ConsultarReportes consultarReportes(Reportes reportes, Clock reloj) {
		return new ConsultarReportes(reportes, reloj);
	}

}
