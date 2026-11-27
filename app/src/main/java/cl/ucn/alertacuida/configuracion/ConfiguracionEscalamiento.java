package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.adapters.seguridad.firma.FirmaWebhookMeta;
import cl.ucn.alertacuida.adapters.web.whatsapp.TokenVerificacionWebhook;
import cl.ucn.alertacuida.adapters.whatsapp.CanalWhatsappCloudApi;
import cl.ucn.alertacuida.adapters.whatsapp.CanalWhatsappNoConfigurado;
import cl.ucn.alertacuida.application.alerta.puerto.AlertasDelReloj;
import cl.ucn.alertacuida.application.aviso.EnviarMensajesWhatsapp;
import cl.ucn.alertacuida.application.aviso.EnviarWhatsappPendientes;
import cl.ucn.alertacuida.application.aviso.GestionarConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.PlantillasWhatsapp;
import cl.ucn.alertacuida.application.aviso.RecibirWebhookWhatsapp;
import cl.ucn.alertacuida.application.aviso.VigilarConversacionesWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.CanalWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.ConversacionesWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.EstadosWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.RegistroEnvios;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.contacto.puerto.ListaDelPaciente;
import cl.ucn.alertacuida.application.escalamiento.AvisarOficina;
import cl.ucn.alertacuida.application.escalamiento.SolicitarAviso;
import cl.ucn.alertacuida.application.escalamiento.VigilarEscalamiento;
import cl.ucn.alertacuida.application.escalamiento.puerto.AvisosOficina;
import cl.ucn.alertacuida.application.escalamiento.puerto.EscalamientosTrabados;
import cl.ucn.alertacuida.application.escalamiento.puerto.IntentosAviso;
import cl.ucn.alertacuida.application.respuesta.ResponderAviso;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.net.URI;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Escalamiento dirigido por el reloj: la solicitud de aviso, el envío de los WhatsApp, el aviso de cada alerta
 * a los coordinadores (oficina PRLAC) y el vigilante que les avisa si el reloj deja de pedir avisos.
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

	/** Aviso de cada alerta a los coordinadores, con la ventana del correo. */
	@Bean
	AvisarOficina avisarOficina(AvisosOficina avisos, CalendarioHabil calendario, PropiedadesCorreo correo,
			Transacciones transacciones, Clock reloj) {
		return new AvisarOficina(avisos, calendario, correo.ventana(), transacciones, reloj);
	}

	/** El reloj pide avisar a un contacto. */
	@Bean
	SolicitarAviso solicitarAviso(AlertasDelReloj alertas, ListaDelPaciente contactos, IntentosAviso intentos,
			Transacciones transacciones, Clock reloj) {
		return new SolicitarAviso(alertas, contactos, intentos, transacciones, reloj);
	}

	/**
	 * @return el canal elegido en alertacuida.whatsapp.canal. Con SIMULADOR o META, si falta una credencial o una
	 *         plantilla, el backend no arranca y dice qué falta
	 */
	@Bean
	CanalWhatsapp canalWhatsapp(PropiedadesWhatsapp propiedades, ObjectMapper json) {
		return switch (propiedades.canal()) {
			case NO_CONFIGURADO -> new CanalWhatsappNoConfigurado();
			case SIMULADOR -> cloudApi(propiedades, exigirSimulador(propiedades), json);
			case META -> cloudApi(propiedades, URI.create("https://graph.facebook.com"), json);
		};
	}

	/** Revisa la firma de los webhooks de Meta con el App Secret. */
	@Bean
	FirmaWebhookMeta firmaWebhookMeta(PropiedadesWhatsapp propiedades) {
		return new FirmaWebhookMeta(propiedades.appSecret());
	}

	/** El Verify token del panel de Meta, para la verificación inicial del webhook. */
	@Bean
	TokenVerificacionWebhook tokenVerificacionWebhook(PropiedadesWhatsapp propiedades) {
		return new TokenVerificacionWebhook(propiedades.verifyToken());
	}

	/** Política de plazos de llamada y recordatorios de llegada por severidad. */
	@Bean
	PoliticaConversacionWhatsapp politicaConversacionWhatsapp(PropiedadesWhatsapp propiedades) {
		return propiedades.conversacion().politica();
	}

	/** Máquina de estados de las conversaciones interactivas de WhatsApp. */
	@Bean
	GestionarConversacionWhatsapp gestionarConversacionWhatsapp(ConversacionesWhatsapp conversaciones,
			PoliticaConversacionWhatsapp politica, Clock reloj) {
		return new GestionarConversacionWhatsapp(conversaciones, politica, reloj);
	}

	/** Vigilante de vencimientos de llamada y recordatorios de llegada en WhatsApp. */
	@Bean
	VigilarConversacionesWhatsapp vigilarConversacionesWhatsapp(ConversacionesWhatsapp conversaciones,
			PoliticaConversacionWhatsapp politica, Transacciones transacciones, Clock reloj) {
		return new VigilarConversacionesWhatsapp(conversaciones, politica, transacciones, reloj);
	}

	/** Envío desacoplado de mensajes de sesión (interactivos y textos dentro de la ventana de 24 h). */
	@Bean
	EnviarMensajesWhatsapp enviarMensajesWhatsapp(ConversacionesWhatsapp conversaciones, CanalWhatsapp canalWhatsapp,
			PropiedadesWhatsapp propiedades, Clock reloj) {
		return new EnviarMensajesWhatsapp(conversaciones, canalWhatsapp,
				new PoliticaReintentos(propiedades.reintentos()), reloj);
	}

	/** Estados de entrega y botones del webhook; despacha respuestas salientes fuera de la transacción HTTP. */
	@Bean
	RecibirWebhookWhatsapp recibirWebhookWhatsapp(EstadosWhatsapp estados, ResponderAviso responder,
			GestionarConversacionWhatsapp conversaciones, EnviarMensajesWhatsapp enviarMensajes,
			PropiedadesWhatsapp propiedades, Transacciones transacciones) {
		Runnable alTerminar = propiedades.conversacion().envioInmediato()
				? () -> CompletableFuture.runAsync(enviarMensajes::enviar)
				: () -> { };
		return new RecibirWebhookWhatsapp(estados, responder::responderIntento, conversaciones, alTerminar,
				transacciones);
	}

	/** @return el canal de la Cloud API con esa dirección base, después de revisar las credenciales */
	private static CanalWhatsapp cloudApi(PropiedadesWhatsapp propiedades, URI base, ObjectMapper json) {
		String canal = "WHATSAPP_CANAL=" + propiedades.canal();
		exigir(propiedades.token(), canal, "WHATSAPP_TOKEN");
		exigir(propiedades.phoneNumberId(), canal, "WHATSAPP_PHONE_NUMBER_ID");
		exigir(propiedades.appSecret(), canal, "WHATSAPP_APP_SECRET");
		exigir(propiedades.verifyToken(), canal, "WHATSAPP_VERIFY_TOKEN");
		PropiedadesWhatsapp.Plantillas plantillas = propiedades.plantillas();
		PlantillasWhatsapp elegidas = new PlantillasWhatsapp(plantillas.nivel1Y2(), plantillas.nivel1Y2SinMapa(),
				plantillas.nivel3Edad(), plantillas.nivel3EdadSinMapa(), plantillas.nivel3Rut(),
				plantillas.nivel3RutSinMapa(), plantillas.simulacro(), plantillas.simulacroSinMapa(),
				plantillas.usarPlantillaRutCarabineros(), propiedades.quienSinAlias());
		return new CanalWhatsappCloudApi(base, propiedades.versionApi(), propiedades.phoneNumberId(),
				propiedades.token(), elegidas, json);
	}

	/** @return la dirección del simulador, que no puede faltar con canal SIMULADOR */
	private static URI exigirSimulador(PropiedadesWhatsapp propiedades) {
		if (propiedades.simuladorUrl() == null) {
			throw new IllegalStateException("Con WHATSAPP_CANAL=SIMULADOR falta WHATSAPP_SIMULADOR_URL");
		}
		return propiedades.simuladorUrl();
	}

	/** @throws IllegalStateException si el valor falta, diciendo qué variable */
	private static void exigir(String valor, String canal, String variable) {
		if (valor == null || valor.isBlank()) {
			throw new IllegalStateException("Con " + canal + " falta " + variable + " (ver docs/despliegue.md)");
		}
	}

	/**
	 * Envío de los WhatsApp pendientes. La política de reintentos se arma aquí y no como bean, para no
	 * confundirla con la del correo.
	 */
	@Bean
	EnviarWhatsappPendientes enviarWhatsappPendientes(BandejaWhatsapp bandeja, RegistroEnvios envios,
			CanalWhatsapp canalWhatsapp, ConversacionesWhatsapp conversaciones, PropiedadesWhatsapp propiedades,
			Clock reloj) {
		return new EnviarWhatsappPendientes(bandeja, envios, canalWhatsapp,
				new PoliticaReintentos(propiedades.reintentos()), reloj, conversaciones);
	}

}
