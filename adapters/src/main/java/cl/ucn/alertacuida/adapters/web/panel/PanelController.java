package cl.ucn.alertacuida.adapters.web.panel;

import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.adapters.web.auditoria.LecturaAuditada;
import cl.ucn.alertacuida.application.panel.ConsultarPanel;
import cl.ucn.alertacuida.application.panel.modelo.Panel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel general de la portada. Lo ve todo el equipo con ALERTAS:VER (decisión del 19-11-2026). */
@RestController
public class PanelController {

	private final ConsultarPanel consultar;

	/** @param consultar caso de uso del panel */
	public PanelController(ConsultarPanel consultar) {
		this.consultar = consultar;
	}

	/** @return los totales de alertas y relojes, y una fila por usuario activo con su reloj */
	@GetMapping("/api/panel")
	@PreAuthorize(Permisos.ALERTAS_VER)
	@LecturaAuditada(entidad = "PACIENTE")
	public Panel consultar() {
		return consultar.consultar();
	}

}
