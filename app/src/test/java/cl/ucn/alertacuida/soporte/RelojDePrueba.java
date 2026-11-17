package cl.ucn.alertacuida.soporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Pone un {@link RelojAjustable} como reloj principal de la aplicación, para que los casos de uso y los
 * controladores tomen la hora que fija la prueba. Parte en {@link EscenarioEscalamiento#LUNES_MEDIODIA}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelojDePrueba {

	/** @return el reloj ajustable, que gana sobre el reloj del sistema */
	@Bean
	@Primary
	RelojAjustable relojAjustable() {
		return new RelojAjustable(EscenarioEscalamiento.LUNES_MEDIODIA);
	}

}
