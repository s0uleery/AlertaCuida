package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.condicion.modelo.Condicion;
import cl.ucn.alertacuida.application.condicion.puerto.Condiciones;
import cl.ucn.alertacuida.domain.paciente.TipoCondicion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Doble de {@link Condiciones} para las pruebas unitarias: guarda en un mapa. */
public final class CondicionesEnMemoria implements Condiciones {

	/** Una condición y el paciente al que pertenece. */
	private record Fila(UUID idPaciente, Condicion condicion) {
	}

	private final Map<UUID, Fila> guardadas = new LinkedHashMap<>();

	@Override
	public List<Condicion> delPaciente(UUID idPaciente) {
		return guardadas.values().stream()
				.filter(fila -> fila.idPaciente().equals(idPaciente))
				.map(Fila::condicion)
				.toList();
	}

	@Override
	public UUID crear(UUID idPaciente, TipoCondicion tipo, String descripcion) {
		UUID id = UUID.randomUUID();
		guardadas.put(id, new Fila(idPaciente, new Condicion(id, tipo, descripcion)));
		return id;
	}

	@Override
	public boolean eliminar(UUID idPaciente, UUID idCondicion) {
		Fila fila = guardadas.get(idCondicion);
		if (fila == null || !fila.idPaciente().equals(idPaciente)) {
			return false;
		}
		guardadas.remove(idCondicion);
		return true;
	}

}
