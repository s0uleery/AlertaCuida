package cl.ucn.alertacuida.application.panel.puerto;

import cl.ucn.alertacuida.application.panel.modelo.FilaPanel;
import cl.ucn.alertacuida.application.panel.modelo.TotalesAlertas;
import java.util.List;

/** Lo que el panel lee de la base, aparte del estado de los relojes. */
public interface PanelUsuarios {

	/** @return los usuarios sin fecha de baja, con su reloj vigente y sus alertas abiertas */
	List<FilaPanel> usuariosActivos();

	/** @return los totales de alertas abiertas, sin respuesta y en seguimiento */
	TotalesAlertas totalesAlertas();

}
