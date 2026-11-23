package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.asignacion.puerto.Emparejamientos;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Doble de {@link Emparejamientos} para las pruebas unitarias: guarda en una lista. */
public final class EmparejamientosEnMemoria implements Emparejamientos {

	/** Una fila de emparejamiento_reloj. */
	public record Fila(String hashCodigo, String hashToken, Instant creacion, Instant expiracion) {
	}

	/** Todas las filas, para que las pruebas las revisen. */
	public final List<Fila> filas = new ArrayList<>();

	@Override
	public boolean guardar(String hashCodigo, String hashToken, Instant creacion, Instant expiracion) {
		if (filas.stream().anyMatch(f -> f.hashCodigo().equals(hashCodigo))) {
			return false;
		}
		return filas.add(new Fila(hashCodigo, hashToken, creacion, expiracion));
	}

	@Override
	public void borrarVencidos(Instant ahora) {
		filas.removeIf(f -> !f.expiracion().isAfter(ahora));
	}

	@Override
	public int pendientes(Instant ahora) {
		return (int) filas.stream().filter(f -> f.expiracion().isAfter(ahora)).count();
	}

	@Override
	public Optional<String> usar(String hashCodigo, Instant ahora) {
		Optional<Fila> fila = filas.stream()
				.filter(f -> f.hashCodigo().equals(hashCodigo) && f.expiracion().isAfter(ahora)).findFirst();
		fila.ifPresent(filas::remove);
		return fila.map(Fila::hashToken);
	}

	@Override
	public boolean pendiente(String hashToken, Instant ahora) {
		return filas.stream().anyMatch(f -> f.hashToken().equals(hashToken) && f.expiracion().isAfter(ahora));
	}

}
