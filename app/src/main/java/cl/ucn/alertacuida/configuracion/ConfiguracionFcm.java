package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.adapters.fcm.CanalAvisoRelojFirebase;
import cl.ucn.alertacuida.adapters.fcm.CanalAvisoRelojSimulado;
import cl.ucn.alertacuida.adapters.fcm.CanalAvisoRelojSimulador;
import cl.ucn.alertacuida.application.avisoreloj.AcusarAvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.AvisarConfiguracionNueva;
import cl.ucn.alertacuida.application.avisoreloj.EnviarAvisosReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.BandejaAvisosReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.CanalAvisoReloj;
import cl.ucn.alertacuida.application.contacto.puerto.AvisoCambioContactos;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Avisos al reloj: el canal elegido en alertacuida.fcm.canal y los casos de uso que lo usan. */
@Configuration
@EnableConfigurationProperties(PropiedadesFcm.class)
public class ConfiguracionFcm {

	/**
	 * @return el canal por el que salen los avisos
	 * @throws IllegalStateException con FIREBASE, si falta la ruta de la llave o no se puede leer
	 *                               o con SIMULADOR, si falta la dirección del simulador
	 */
	@Bean
	CanalAvisoReloj canalAvisoReloj(PropiedadesFcm propiedades, ObjectMapper json) {
		return switch (propiedades.canal()) {
			case FIREBASE -> {
				if (propiedades.credenciales() == null || propiedades.credenciales().isBlank()) {
					throw new IllegalStateException("Con FCM_CANAL=FIREBASE falta FCM_CREDENCIALES");
				}
				yield CanalAvisoRelojFirebase.desdeArchivo(propiedades.credenciales());
			}
			case SIMULADO -> new CanalAvisoRelojSimulado();
			case SIMULADOR -> {
				if (propiedades.simuladorUrl() == null) {
					throw new IllegalStateException("Con FCM_CANAL=SIMULADOR falta FCM_SIMULADOR_URL");
				}
				yield new CanalAvisoRelojSimulador(propiedades.simuladorUrl(), json);
			}
		};
	}

	/** Envío de la bandeja de avisos, con reintentos mientras el reloj no acuse. */
	@Bean
	EnviarAvisosReloj enviarAvisosReloj(BandejaAvisosReloj bandeja, CanalAvisoReloj canal, PropiedadesFcm propiedades,
			Clock reloj) {
		return new EnviarAvisosReloj(bandeja, canal, propiedades.politica(), reloj);
	}

	/** Acuse de los avisos por parte del reloj. */
	@Bean
	AcusarAvisoReloj acusarAvisoReloj(BandejaAvisosReloj bandeja) {
		return new AcusarAvisoReloj(bandeja);
	}

	/** Aviso de que cambió la lista de contactos de un paciente. */
	@Bean
	AvisoCambioContactos avisoCambioContactos(BandejaAvisosReloj bandeja, CanalAvisoReloj canal) {
		return new AvisarConfiguracionNueva(bandeja, canal);
	}

}
