package cl.ucn.alertacuida.adapters.web.alerta;

import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.alerta.RegistrarSeguimiento;
import cl.ucn.alertacuida.application.alerta.modelo.DestinoDerivacion;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de destinos a los que se puede derivar un caso, para el formulario de la ficha de la alerta. */
@RestController
public class DestinosDerivacionController {

	private final RegistrarSeguimiento seguimiento;

	/** @param seguimiento da los destinos activos */
	public DestinosDerivacionController(RegistrarSeguimiento seguimiento) {
		this.seguimiento = seguimiento;
	}

	/** @return los destinos activos, por nombre */
	@GetMapping("/api/destinos-derivacion")
	@PreAuthorize(Permisos.ALERTAS_VER)
	public List<DestinoDerivacion> destinos() {
		return seguimiento.destinos();
	}

}
