package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.respuesta.ResponderAviso;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.RespuestasContacto;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Une la respuesta del contacto desde su correo con sus adaptadores. */
@Configuration
public class ConfiguracionRespuestaContacto {

	/** "Confirmo que voy" y "No puedo asistir" desde los enlaces del correo. */
	@Bean
	ResponderAviso responderAviso(FirmaRespuesta firma, RespuestasContacto respuestas, Transacciones transacciones,
			Clock reloj) {
		return new ResponderAviso(firma, respuestas, transacciones, reloj);
	}

}
