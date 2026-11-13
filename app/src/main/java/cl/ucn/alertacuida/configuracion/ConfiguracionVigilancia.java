package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.application.calendario.ReglaEnrutamiento;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.vigilancia.ReglasAusencia;
import cl.ucn.alertacuida.application.vigilancia.VigilarRelojes;
import cl.ucn.alertacuida.application.vigilancia.puerto.RelojesVigilados;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Vigilante de los relojes, unido con sus adaptadores. */
@Configuration
@EnableConfigurationProperties(PropiedadesVigilancia.class)
public class ConfiguracionVigilancia {

	/** Revisión periódica de los relojes con las reglas de ausencia. */
	@Bean
	VigilarRelojes vigilarRelojes(RelojesVigilados relojes, Alertas alertas, PropiedadesVigilancia propiedades,
			ReglaEnrutamiento enrutamiento, Transacciones transacciones, Clock reloj) {
		return new VigilarRelojes(relojes, alertas, ReglasAusencia.todas(propiedades.parametros()), enrutamiento,
				transacciones, reloj);
	}

}
