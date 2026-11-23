package cl.ucn.alertacuida.application.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaAtendible;
import cl.ucn.alertacuida.application.alerta.modelo.DestinoDerivacion;
import cl.ucn.alertacuida.application.alerta.modelo.TipoSeguimiento;
import cl.ucn.alertacuida.application.alerta.puerto.Seguimientos;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Acciones del equipo sobre un caso (decisión del 19-11-2026): llamada, visita y nota exigen el texto; la
 * derivación exige un destino activo y el texto es opcional. Se registran mientras la alerta no esté cerrada
 * del todo ni cancelada, y quedan auditadas.
 */
class RegistrarSeguimientoTest {

	private static final UUID ALERTA = UUID.randomUUID();
	private static final UUID USUARIO = UUID.randomUUID();

	/** Ejecuta el trabajo sin transacción real. */
	private static final Transacciones SIN_TRANSACCION = new Transacciones() {

		@Override
		public <T> T ejecutar(Supplier<T> trabajo) {
			return trabajo.get();
		}

	};

	private final Falsos falsos = new Falsos();
	private final RegistrarSeguimiento registrar = new RegistrarSeguimiento(falsos::buscar, falsos, falsos.auditoria,
			SIN_TRANSACCION, Clock.fixed(Instant.parse("2026-11-19T13:00:00Z"), ZoneOffset.UTC));

	/** Llamada, visita y nota con texto se guardan sin destino, con el texto sin espacios de más, y se auditan. */
	@ParameterizedTest
	@EnumSource(value = TipoSeguimiento.class, names = {"LLAMADA", "VISITA", "NOTA"})
	void conTextoSeGuardan(TipoSeguimiento tipo) {
		UUID id = registrar.registrar(ALERTA, tipo, null, "  Se llamó a la hija  ", USUARIO, "1.1.1.1");

		assertThat(falsos.guardado).containsExactly(tipo.name(), null, "Se llamó a la hija");
		assertThat(falsos.auditadas).containsExactly("CREAR SEGUIMIENTO " + id);
	}

	/** Sin texto, o con un destino, se rechazan. */
	@Test
	void sinTextoOConDestinoSeRechaza() {
		assertThatThrownBy(() -> registrar.registrar(ALERTA, TipoSeguimiento.NOTA, null, "   ", USUARIO, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> registrar.registrar(ALERTA, TipoSeguimiento.LLAMADA, "CESFAM", "x", USUARIO, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> registrar.registrar(ALERTA, null, null, "x", USUARIO, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThat(falsos.guardado).isEmpty();
	}

	/** La derivación exige un destino activo; el texto es opcional. */
	@Test
	void laDerivacionExigeUnDestinoActivo() {
		assertThatThrownBy(() -> registrar.registrar(ALERTA, TipoSeguimiento.DERIVACION, null, "x", USUARIO, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> registrar.registrar(ALERTA, TipoSeguimiento.DERIVACION, "INVENTADO", null, USUARIO,
				"ip")).isInstanceOf(SolicitudRechazada.class);

		registrar.registrar(ALERTA, TipoSeguimiento.DERIVACION, "CESFAM", null, USUARIO, "ip");
		assertThat(falsos.guardado).containsExactly("DERIVACION", "CESFAM", null);
	}

	/** Alerta inexistente, 404; cancelada por el usuario, 409; ya cerrada del todo, 409. */
	@Test
	void alertaInexistenteCanceladaOCerrada() {
		falsos.alerta = null;
		assertThatThrownBy(() -> registrar.registrar(ALERTA, TipoSeguimiento.NOTA, null, "x", USUARIO, "ip"))
				.isInstanceOf(NoEncontrado.class);

		falsos.alerta = new AlertaAtendible(ALERTA, TipoEvento.CAIDA, true, true);
		assertThatThrownBy(() -> registrar.registrar(ALERTA, TipoSeguimiento.NOTA, null, "x", USUARIO, "ip"))
				.isInstanceOf(Conflicto.class);

		falsos.alerta = new AlertaAtendible(ALERTA, TipoEvento.CAIDA, true, false);
		falsos.abierta = false;
		assertThatThrownBy(() -> registrar.registrar(ALERTA, TipoSeguimiento.NOTA, null, "x", USUARIO, "ip"))
				.isInstanceOf(Conflicto.class);
		assertThat(falsos.auditadas).isEmpty();
	}

	/** Puertos falsos: una alerta abierta y el destino CESFAM activo. */
	private static final class Falsos implements Seguimientos {

		private AlertaAtendible alerta = new AlertaAtendible(ALERTA, TipoEvento.CAIDA, true, false);
		private boolean abierta = true;
		private final List<String> guardado = new ArrayList<>();
		private final List<String> auditadas = new ArrayList<>();
		private final RegistroAuditoria auditoria = new RegistroAuditoria() {

			@Override
			public void registrar(UUID idUsuario, String accion, String entidad, String idEntidad, String ip,
					Instant fechaHora, Map<String, Object> detalle) {
				auditadas.add(accion + " " + entidad + " " + idEntidad);
			}

		};

		/** @return la alerta, si existe */
		Optional<AlertaAtendible> buscar(UUID idAlerta) {
			return Optional.ofNullable(alerta);
		}

		@Override
		public Optional<UUID> registrar(UUID idAlerta, UUID idUsuario, TipoSeguimiento tipo, String destino,
				String nota, Instant ahora) {
			if (!abierta) {
				return Optional.empty();
			}
			guardado.add(tipo.name());
			guardado.add(destino);
			guardado.add(nota);
			return Optional.of(UUID.randomUUID());
		}

		@Override
		public boolean destinoActivo(String codigo) {
			return "CESFAM".equals(codigo);
		}

		@Override
		public List<DestinoDerivacion> destinosActivos() {
			return List.of(new DestinoDerivacion("CESFAM", "CESFAM"));
		}

	}

}
