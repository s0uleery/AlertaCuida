package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.application.sesion.IniciarSesion;
import cl.ucn.alertacuida.application.sesion.PoliticaBloqueo;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import cl.ucn.alertacuida.application.sesion.puerto.EmisorToken;
import cl.ucn.alertacuida.application.sesion.puerto.HashRun;
import cl.ucn.alertacuida.application.sesion.puerto.RegistroAccesos;
import cl.ucn.alertacuida.application.sesion.puerto.VerificadorContrasena;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Une los casos de uso (Java puro, en application) con sus adaptadores. Es el único lugar
 * que conoce ambos lados: los casos de uso solo ven interfaces.
 */
@Configuration
public class ConfiguracionCasosDeUso {

	@Bean
	Clock reloj() {
		return Clock.systemUTC();
	}

	@Bean
	IniciarSesion iniciarSesion(CuentaUsuarioRepository cuentas, RegistroAccesos accesos, HashRun hashRun,
			VerificadorContrasena verificador, EmisorToken emisor, PropiedadesSeguridad propiedades, Clock reloj) {
		PropiedadesSeguridad.Bloqueo bloqueo = propiedades.bloqueo();
		return new IniciarSesion(cuentas, accesos, hashRun, verificador, emisor,
				new PoliticaBloqueo(bloqueo.intentos(), bloqueo.ventana()), reloj);
	}

}
