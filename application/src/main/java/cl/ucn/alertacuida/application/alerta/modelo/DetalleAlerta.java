package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.CategoriaAlerta;
import cl.ucn.alertacuida.domain.alerta.EstadoAtencion;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una alerta con todo su recorrido, para su ficha. Los campos comunes son los de {@link ResumenAlerta}.
 *
 * @param atencion      cómo se cerró; null mientras siga abierta
 * @param lineaTiempo   los pasos, del más antiguo al más reciente
 * @param evidencia     lo que explica la alerta; con la lista de lecturas vacía si no trae
 * @param discrepancias lo que no calzó con la configuración vigente al recibirla; vacía si nada
 */
public record DetalleAlerta(UUID id, long numero, PacienteDeAlerta paciente, TipoEvento tipoEvento,
		Severidad severidad, EstadoContacto estadoContacto, EstadoAtencion estadoAtencion, String responsable,
		Instant recepcion, boolean prioridadMaxima, CategoriaAlerta categoria, boolean simulacro,
		String contactoEnCurso, CierreAlerta atencion, List<PasoAlerta> lineaTiempo, EvidenciaAlerta evidencia,
		List<Discrepancia> discrepancias) {

	/** @return el detalle, con los datos comunes tomados del resumen */
	public static DetalleAlerta de(ResumenAlerta resumen, CierreAlerta atencion, List<PasoAlerta> lineaTiempo,
			EvidenciaAlerta evidencia, List<Discrepancia> discrepancias) {
		return new DetalleAlerta(resumen.id(), resumen.numero(), resumen.paciente(), resumen.tipoEvento(),
				resumen.severidad(), resumen.estadoContacto(), resumen.estadoAtencion(), resumen.responsable(),
				resumen.recepcion(), resumen.prioridadMaxima(), resumen.categoria(), resumen.simulacro(),
				resumen.contactoEnCurso(), atencion, lineaTiempo, evidencia, discrepancias);
	}

}
