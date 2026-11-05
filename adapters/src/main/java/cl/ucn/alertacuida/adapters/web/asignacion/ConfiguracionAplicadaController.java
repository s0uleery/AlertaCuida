package cl.ucn.alertacuida.adapters.web.asignacion;

import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.configuracion.ConsultarConfiguracionAplicada;
import cl.ucn.alertacuida.application.configuracion.modelo.ConfiguracionAplicada;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Si el reloj de un paciente ya aplica la configuración vigente, para su ficha. Exige USUARIOS:VER. */
@RestController
public class ConfiguracionAplicadaController {

	private final ConsultarConfiguracionAplicada consultar;

	/** @param consultar compara la versión que informó el reloj con la vigente */
	public ConfiguracionAplicadaController(ConsultarConfiguracionAplicada consultar) {
		this.consultar = consultar;
	}

	/** @return al día, pendiente, sin sincronizar o sin reloj, con las fechas. 404 si el paciente no existe */
	@GetMapping("/api/pacientes/{idPaciente}/reloj/configuracion")
	@PreAuthorize(Permisos.USUARIOS_VER)
	@LecturaAuditada(entidad = "ASIGNACION_RELOJ", idVariable = "idPaciente")
	public ConfiguracionAplicada consultar(@PathVariable UUID idPaciente) {
		return consultar.consultar(idPaciente);
	}

}
