package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.alerta.AtenderAlerta;
import cl.ucn.alertacuida.application.alerta.ConsultarAlertas;
import cl.ucn.alertacuida.application.alerta.RegistrarSeguimiento;
import cl.ucn.alertacuida.application.alerta.puerto.AtencionAlertas;
import cl.ucn.alertacuida.application.alerta.puerto.ConsultaAlertas;
import cl.ucn.alertacuida.application.alerta.puerto.Seguimientos;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.clinica.modelo.ValoresBase;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.redseguridad.ConsultarHallazgos;
import cl.ucn.alertacuida.application.redseguridad.puerto.Hallazgos;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Une los casos de uso de las alertas en el dashboard y de la red de seguridad con sus adaptadores. */
@Configuration
public class ConfiguracionAtencionAlertas {

	/** Lista de alertas y detalle con su línea de tiempo. */
	@Bean
	ConsultarAlertas consultarAlertas(ConsultaAlertas consulta, PropiedadesFcm fcm, Clock reloj) {
		return new ConsultarAlertas(consulta, fcm.politica(), reloj);
	}

	/** Tomar un caso, resolverlo y cerrar su seguimiento. */
	@Bean
	AtenderAlerta atenderAlerta(AtencionAlertas atencion, RegistroAuditoria auditoria, Transacciones transacciones,
			Clock reloj) {
		return new AtenderAlerta(atencion, auditoria, transacciones, reloj);
	}

	/** Llamadas, visitas, derivaciones y notas de un caso. */
	@Bean
	RegistrarSeguimiento registrarSeguimiento(AtencionAlertas atencion, Seguimientos seguimientos,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new RegistrarSeguimiento(atencion::buscar, seguimientos, auditoria, transacciones, reloj);
	}

	/** Red de seguridad: lecturas graves sin alerta del reloj cerca, para auditarlo. */
	@Bean
	ConsultarHallazgos consultarHallazgos(Hallazgos hallazgos, ValoresBase base, Clock reloj) {
		return new ConsultarHallazgos(hallazgos, base, reloj);
	}

}
