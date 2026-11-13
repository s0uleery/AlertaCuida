package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.adapters.whatsapp.CanalWhatsappNoConfigurado;
import cl.ucn.alertacuida.application.alerta.puerto.AlertasDelReloj;
import cl.ucn.alertacuida.application.aviso.EnviarWhatsappPendientes;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.CanalWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.RegistroEnvios;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.contacto.puerto.ListaDelPaciente;
import cl.ucn.alertacuida.application.escalamiento.SolicitarAviso;
import cl.ucn.alertacuida.application.escalamiento.VigilarEscalamiento;
import cl.ucn.alertacuida.application.escalamiento.puerto.EscalamientosTrabados;
import cl.ucn.alertacuida.application.escalamiento.puerto.IntentosAviso;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Escalamiento dirigido por el reloj: la solicitud de aviso, el envío de los WhatsApp que pide y el vigilante
 * que avisa a los coordinadores si el reloj deja de pedir avisos.
 */
@Configuration
@EnableConfigurationProperties({PropiedadesWhatsapp.class, PropiedadesEscalamiento.class})
public class ConfiguracionEscalamiento {

	/** Vigilante del escalamiento, con el plazo propio y la ventana del correo. */
	@Bean
	VigilarEscalamiento vigilarEscalamiento(EscalamientosTrabados trabados, CalendarioHabil calendario,
			PropiedadesEscalamiento escalamiento, PropiedadesCorreo correo, Transacciones transacciones,
			Clock reloj) {
		return new VigilarEscalamiento(trabados, calendario, escalamiento.plazo(), correo.ventana(), transacciones,
				reloj);
	}

	/** El reloj pide avisar a un contacto. */
	@Bean
	SolicitarAviso solicitarAviso(AlertasDelReloj alertas, ListaDelPaciente contactos, IntentosAviso intentos,
			Transacciones transacciones, Clock reloj) {
		return new SolicitarAviso(alertas, contactos, intentos, transacciones, reloj);
	}

	/** @return el canal elegido en alertacuida.whatsapp.canal */
	@Bean
	CanalWhatsapp canalWhatsapp(PropiedadesWhatsapp propiedades) {
		return switch (propiedades.canal()) {
			case NO_CONFIGURADO -> new CanalWhatsappNoConfigurado();
		};
	}

	/**
	 * Envío de los WhatsApp pendientes. La política de reintentos se arma aquí y no como bean, para no
	 * confundirla con la del correo.
	 */
	@Bean
	EnviarWhatsappPendientes enviarWhatsappPendientes(BandejaWhatsapp bandeja, RegistroEnvios envios,
			CanalWhatsapp canalWhatsapp, PropiedadesWhatsapp propiedades, Clock reloj) {
		return new EnviarWhatsappPendientes(bandeja, envios, canalWhatsapp,
				new PoliticaReintentos(propiedades.reintentos()), reloj);
	}

}
