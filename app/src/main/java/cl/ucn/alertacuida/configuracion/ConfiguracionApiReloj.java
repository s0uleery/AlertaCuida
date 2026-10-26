package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.alerta.RecibirAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.application.asignacion.AutenticarReloj;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.telemetria.RecibirSincronizacion;
import cl.ucn.alertacuida.application.telemetria.puerto.Sincronizaciones;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso de la API del reloj, unidos con sus adaptadores. Igual que
 * {@link ConfiguracionPacientesYRelojes}, que tiene los del dashboard.
 */
@Configuration
public class ConfiguracionApiReloj {

	/** Reconoce al reloj por su token en cada petición. */
	@Bean
	AutenticarReloj autenticarReloj(Asignaciones asignaciones, SecretosUnSoloUso secretos) {
		return new AutenticarReloj(asignaciones, secretos);
	}

	/** Lotes de estado y lecturas que el reloj envía cada 2 horas. */
	@Bean
	RecibirSincronizacion recibirSincronizacion(Sincronizaciones sincronizaciones, Transacciones transacciones,
			Clock reloj) {
		return new RecibirSincronizacion(sincronizaciones, transacciones, reloj);
	}

	/** Alertas del reloj, cancelaciones del paciente y estado de cada alerta. */
	@Bean
	RecibirAlerta recibirAlerta(Alertas alertas, Clock reloj) {
		return new RecibirAlerta(alertas, reloj);
	}

}
