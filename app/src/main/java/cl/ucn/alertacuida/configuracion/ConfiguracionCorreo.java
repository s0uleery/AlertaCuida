package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.adapters.correo.CanalCorreoSimulado;
import cl.ucn.alertacuida.adapters.correo.CanalCorreoSmtp;
import cl.ucn.alertacuida.application.aviso.EncolarCorreos;
import cl.ucn.alertacuida.application.aviso.EnviarCorreosPendientes;
import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import cl.ucn.alertacuida.application.aviso.puerto.ColaCorreos;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Correo institucional: el canal elegido en alertacuida.correo.canal y los casos de uso que lo usan. */
@Configuration
@EnableConfigurationProperties(PropiedadesCorreo.class)
public class ConfiguracionCorreo {

	/**
	 * @param smtp el cliente SMTP que arma Spring con spring.mail.*; solo se pide si el canal es SMTP
	 * @return el canal por el que salen los correos
	 */
	@Bean
	CanalNotificacion canalCorreo(PropiedadesCorreo propiedades, ObjectProvider<JavaMailSender> smtp) {
		return switch (propiedades.canal()) {
			case SMTP -> new CanalCorreoSmtp(smtp.getObject(), propiedades.remitente());
			case SIMULADO -> new CanalCorreoSimulado();
		};
	}

	/**
	 * Plantillas con la dirección del dashboard, los textos de correos.yml y la firma de los enlaces con que
	 * el contacto responde.
	 */
	@Bean
	PlantillasCorreo plantillasCorreo(PropiedadesCorreo propiedades, FirmaRespuesta firma) {
		return new PlantillasCorreo(propiedades.urlDashboard(), propiedades.textos(), firma);
	}

	/** Esperas entre reintentos de un correo fallido. */
	@Bean
	PoliticaReintentos politicaReintentosCorreo(PropiedadesCorreo propiedades) {
		return new PoliticaReintentos(propiedades.reintentos());
	}

	/** Qué correos se encolan: al equipo en horario laboral y los respaldos de WhatsApp. */
	@Bean
	EncolarCorreos encolarCorreos(ColaCorreos cola, CalendarioHabil calendario, PropiedadesCorreo propiedades,
			Transacciones transacciones, Clock reloj) {
		return new EncolarCorreos(cola, calendario, propiedades.ventana(), transacciones, reloj);
	}

	/** Envío de la bandeja de salida, con reintentos. */
	@Bean
	EnviarCorreosPendientes enviarCorreosPendientes(BandejaSalida bandeja, CanalNotificacion canalCorreo,
			PlantillasCorreo plantillas, PoliticaReintentos politicaReintentosCorreo, Clock reloj) {
		return new EnviarCorreosPendientes(bandeja, canalCorreo, plantillas, politicaReintentosCorreo, reloj);
	}

}
