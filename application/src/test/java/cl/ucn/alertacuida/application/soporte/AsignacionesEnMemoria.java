package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionHistorial;
import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionVigente;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Doble de {@link Asignaciones} para las pruebas unitarias: guarda en una lista. */
public final class AsignacionesEnMemoria implements Asignaciones {

	/** Una fila de asignacion_reloj. */
	public static final class Fila {
		public final UUID id = UUID.randomUUID();
		public final UUID idDispositivo;
		public final UUID idPaciente;
		public final Instant inicio;
		public Instant fin;
		public String hashToken;

		Fila(UUID idDispositivo, UUID idPaciente, Instant inicio, String hashToken) {
			this.idDispositivo = idDispositivo;
			this.idPaciente = idPaciente;
			this.inicio = inicio;
			this.hashToken = hashToken;
		}
	}

	/** Todas las filas, para que las pruebas revisen el historial. */
	public final List<Fila> filas = new ArrayList<>();

	@Override
	public Optional<AsignacionVigente> vigenteDelPaciente(UUID idPaciente) {
		return filas.stream().filter(f -> f.fin == null && f.idPaciente.equals(idPaciente)).findFirst()
				.map(this::vigente);
	}

	@Override
	public Optional<AsignacionVigente> vigenteDelReloj(UUID idDispositivo) {
		return filas.stream().filter(f -> f.fin == null && f.idDispositivo.equals(idDispositivo)).findFirst()
				.map(this::vigente);
	}

	@Override
	public Map<UUID, String> relojesVigentesPorPaciente() {
		return filas.stream().filter(f -> f.fin == null)
				.collect(Collectors.toMap(f -> f.idPaciente, f -> f.idDispositivo.toString()));
	}

	@Override
	public List<AsignacionHistorial> historialDelPaciente(UUID idPaciente) {
		return filas.stream().filter(f -> f.idPaciente.equals(idPaciente))
				.sorted(Comparator.comparing((Fila f) -> f.inicio).reversed())
				.map(f -> new AsignacionHistorial(f.id, f.idDispositivo, f.idDispositivo.toString(), f.inicio, f.fin))
				.toList();
	}

	@Override
	public UUID abrir(UUID idDispositivo, UUID idPaciente, Instant inicio, String hashToken) {
		Fila fila = new Fila(idDispositivo, idPaciente, inicio, hashToken);
		filas.add(fila);
		return fila.id;
	}

	@Override
	public void cerrar(UUID idAsignacion, Instant fin) {
		fila(idAsignacion).fin = fin;
	}

	@Override
	public void cambiarToken(UUID idAsignacion, String hashToken) {
		fila(idAsignacion).hashToken = hashToken;
	}

	private Fila fila(UUID id) {
		return filas.stream().filter(f -> f.id.equals(id)).findFirst().orElseThrow();
	}

	private AsignacionVigente vigente(Fila f) {
		return new AsignacionVigente(f.id, f.idDispositivo, f.idPaciente);
	}

}
