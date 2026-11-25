package cl.ucn.alertacuida.application.reloj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.reloj.puerto.BajaRelojes;
import cl.ucn.alertacuida.application.soporte.AsignacionesEnMemoria;
import cl.ucn.alertacuida.application.soporte.RelojesEnMemoria;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Eliminar un reloj sin base de datos (decisión del 24-11-2026): se borra solo si nunca tuvo paciente; si está
 * vinculado responde EN_USO y si tuvo paciente, CON_HISTORIAL.
 */
class EliminarRelojTest {

	private static final Instant AHORA = Instant.parse("2026-11-24T12:00:00Z");
	private static final UUID ADMIN = UUID.randomUUID();

	private final RelojesEnMemoria relojes = new RelojesEnMemoria();
	private final AsignacionesEnMemoria asignaciones = new AsignacionesEnMemoria();
	private final Set<UUID> borrados = new HashSet<>();
	private final List<String> auditoria = new ArrayList<>();
	private EliminarReloj eliminar;

	/** El caso de uso con dobles en memoria; "tuvo asignaciones" sale de las filas del doble de asignaciones. */
	@BeforeEach
	void preparar() {
		BajaRelojes baja = new BajaRelojes() {
			@Override
			public boolean tuvoAsignaciones(UUID id) {
				return asignaciones.filas.stream().anyMatch(fila -> fila.idDispositivo.equals(id));
			}

			@Override
			public boolean eliminarSinHistorial(UUID id) {
				return !tuvoAsignaciones(id) && borrados.add(id);
			}
		};
		Transacciones enTransaccion = Supplier::get;
		eliminar = new EliminarReloj(relojes, asignaciones, baja,
				(usuario, accion, entidad, id, ip, fecha, detalle) -> auditoria.add(accion + " " + entidad + " " + id),
				enTransaccion, Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	/** Un reloj sin historial se borra y queda auditado. */
	@Test
	void unRelojSinHistorialSeBorra() {
		UUID reloj = relojes.agregar("R-1", EstadoReloj.OPERATIVO);

		eliminar.eliminar(reloj, ADMIN, "ip");

		assertThat(borrados).containsExactly(reloj);
		assertThat(auditoria).containsExactly("ELIMINAR SMARTWATCH " + reloj);
	}

	/** Un reloj vinculado ahora responde EN_USO. */
	@Test
	void unRelojEnUsoNoSeBorra() {
		UUID reloj = relojes.agregar("R-1", EstadoReloj.OPERATIVO);
		asignaciones.abrir(reloj, UUID.randomUUID(), AHORA.minusSeconds(3600), "token");

		assertThatThrownBy(() -> eliminar.eliminar(reloj, ADMIN, "ip")).isInstanceOf(Conflicto.class)
				.satisfies(error -> assertThat(((Conflicto) error).codigo()).isEqualTo(EliminarReloj.EN_USO));
		assertThat(borrados).isEmpty();
		assertThat(auditoria).isEmpty();
	}

	/** Un reloj que tuvo paciente responde CON_HISTORIAL. */
	@Test
	void unRelojConHistorialNoSeBorra() {
		UUID reloj = relojes.agregar("R-1", EstadoReloj.OPERATIVO);
		UUID vinculo = asignaciones.abrir(reloj, UUID.randomUUID(), AHORA.minusSeconds(7200), "token");
		asignaciones.cerrar(vinculo, AHORA.minusSeconds(3600));

		assertThatThrownBy(() -> eliminar.eliminar(reloj, ADMIN, "ip")).isInstanceOf(Conflicto.class)
				.satisfies(error -> assertThat(((Conflicto) error).codigo()).isEqualTo(EliminarReloj.CON_HISTORIAL));
		assertThat(borrados).isEmpty();
	}

	/** Un reloj que no existe responde 404. */
	@Test
	void unRelojQueNoExisteResponde404() {
		assertThatThrownBy(() -> eliminar.eliminar(UUID.randomUUID(), ADMIN, "ip"))
				.isInstanceOf(NoEncontrado.class);
	}

}
