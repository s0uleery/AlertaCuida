package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.paciente.modelo.Paciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Doble de {@link Pacientes} para las pruebas unitarias: guarda en un mapa. */
public final class PacientesEnMemoria implements Pacientes {

	private final Map<UUID, Paciente> guardados = new LinkedHashMap<>();
	private final Map<UUID, String> runHash = new LinkedHashMap<>();

	@Override
	public boolean existeRunHash(String hash, UUID excepto) {
		return runHash.entrySet().stream()
				.anyMatch(fila -> fila.getValue().equals(hash) && !fila.getKey().equals(excepto));
	}

	@Override
	public UUID crear(DatosPaciente datos, String hash) {
		UUID id = UUID.randomUUID();
		guardados.put(id, new Paciente(id, datos, null));
		runHash.put(id, hash);
		return id;
	}

	@Override
	public void actualizar(UUID id, DatosPaciente datos, String hash) {
		guardados.put(id, new Paciente(id, datos, guardados.get(id).fechaBaja()));
		runHash.put(id, hash);
	}

	@Override
	public Optional<Paciente> buscar(UUID id) {
		return Optional.ofNullable(guardados.get(id));
	}

	@Override
	public List<Paciente> listar(boolean incluirBajas) {
		return guardados.values().stream()
				.filter(paciente -> incluirBajas || paciente.activo())
				.sorted(Comparator.comparing(paciente -> Objects.toString(paciente.datos().apellidos())))
				.toList();
	}

	@Override
	public void darDeBaja(UUID id, LocalDate fecha) {
		Paciente paciente = guardados.get(id);
		guardados.put(id, new Paciente(id, paciente.datos(), fecha));
	}

}
