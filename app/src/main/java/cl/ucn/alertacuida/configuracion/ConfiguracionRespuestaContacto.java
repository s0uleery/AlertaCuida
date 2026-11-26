package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.alerta.AtenderAlerta;
import cl.ucn.alertacuida.application.alerta.RegistrarSeguimiento;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.aviso.RecordarLlegada;
import cl.ucn.alertacuida.application.aviso.puerto.RecordatoriosLlegada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.respuesta.ArmarVistaOficina;
import cl.ucn.alertacuida.application.respuesta.AtenderDesdeEnlace;
import cl.ucn.alertacuida.application.respuesta.ResponderAviso;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.PasosOficina;
import cl.ucn.alertacuida.application.respuesta.puerto.RespuestasContacto;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Une la página del enlace del correo con sus adaptadores: la respuesta del contacto del paciente y los pasos de
 * la oficina PRLAC, con su recordatorio de llegada.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesOficina.class)
public class ConfiguracionRespuestaContacto {

	/** Calcula la etapa de la oficina, con el plazo del recordatorio. */
	@Bean
	ArmarVistaOficina armarVistaOficina(PasosOficina pasos, PropiedadesOficina oficina, Clock reloj) {
		return new ArmarVistaOficina(pasos, oficina.recordatorioLlegada(), reloj);
	}

	/** "Confirmo que voy", "No puedo asistir", la llamada y la llegada desde los enlaces del correo. */
	@Bean
	ResponderAviso responderAviso(FirmaRespuesta firma, RespuestasContacto respuestas, ArmarVistaOficina vistas,
			Transacciones transacciones, Clock reloj) {
		return new ResponderAviso(firma, respuestas, vistas::de, transacciones, reloj);
	}

	/** Los pasos de la oficina desde el enlace de un coordinador. */
	@Bean
	AtenderDesdeEnlace atenderDesdeEnlace(FirmaRespuesta firma, RespuestasContacto respuestas, PasosOficina pasos,
			ResponderAviso responder, AtenderAlerta atender, RegistrarSeguimiento seguimiento,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new AtenderDesdeEnlace(firma, respuestas, pasos, responder, atender, seguimiento, auditoria,
				transacciones, reloj);
	}

	/** Recordatorio de llegada del personal, con la ventana del correo. */
	@Bean
	RecordarLlegada recordarLlegada(RecordatoriosLlegada recordatorios, PropiedadesOficina oficina,
			PropiedadesCorreo correo, Transacciones transacciones, Clock reloj) {
		return new RecordarLlegada(recordatorios, oficina.recordatorioLlegada(), correo.ventana(), transacciones,
				reloj);
	}

}
