package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.alerta.AtenderAlerta;
import cl.ucn.alertacuida.application.alerta.ConsultarAlertas;
import cl.ucn.alertacuida.application.alerta.puerto.AtencionAlertas;
import cl.ucn.alertacuida.application.alerta.puerto.ConsultaAlertas;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Une los casos de uso de las alertas en el dashboard con sus adaptadores. */
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

}
