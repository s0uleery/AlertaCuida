package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.reloj.modelo.DatosReloj;
import cl.ucn.alertacuida.application.reloj.modelo.Reloj;
import cl.ucn.alertacuida.application.reloj.modelo.RelojConPaciente;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de {@link Relojes} para las pruebas unitarias: guarda en un mapa. */
public final class RelojesEnMemoria implements Relojes {

	private final Map<UUID, Reloj> guardados = new LinkedHashMap<>();

	/** Atajo de las pruebas: registra un reloj con ese identificador y estado. */
	public UUID agregar(String identificador, EstadoReloj estado) {
		return crear(new DatosReloj(identificador, null, null, null, null, estado));
	}

	@Override
	public boolean existeIdentificador(String identificadorReloj, UUID excepto) {
		return guardados.values().stream()
				.anyMatch(r -> r.datos().identificadorReloj().equals(identificadorReloj) && !r.id().equals(excepto));
	}

	@Override
	public UUID crear(DatosReloj datos) {
		UUID id = UUID.randomUUID();
		guardados.put(id, new Reloj(id, datos));
		return id;
	}

	@Override
	public void actualizar(UUID id, DatosReloj datos) {
		guardados.put(id, new Reloj(id, datos));
	}

	@Override
	public void cambiarEstado(UUID id, EstadoReloj estado) {
		DatosReloj d = guardados.get(id).datos();
		actualizar(id, new DatosReloj(d.identificadorReloj(), d.modelo(), d.iccid(), d.numeroSim(),
				d.fechaVencimientoPlan(), estado));
	}

	@Override
	public Optional<Reloj> buscar(UUID id) {
		return Optional.ofNullable(guardados.get(id));
	}

	@Override
	public List<RelojConPaciente> listar() {
		return guardados.values().stream().map(r -> new RelojConPaciente(r, null, null)).toList();
	}

}
