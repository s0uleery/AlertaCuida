package cl.ucn.alertacuida.application.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.modelo.CuentaEquipo;
import cl.ucn.alertacuida.application.personal.modelo.PerfilEquipo;
import cl.ucn.alertacuida.application.personal.puerto.EquipoPersonal;
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

/**
 * Cambiar el celular con WhatsApp y el opt-in de una cuenta ya creada (decisión del 19-11-2026: lo hace
 * ADMINISTRACION:EDITAR). Las reglas son las del alta y del cambio de rol: un coordinador no queda sin teléfono
 * y no se acepta WhatsApp sin teléfono.
 */
class CambiarTelefonoPersonalTest {

	private static final Instant AHORA = Instant.parse("2026-11-19T13:00:00Z");
	private static final UUID CUENTA = UUID.randomUUID();
	private static final UUID ADMIN = UUID.randomUUID();

	/** Ejecuta el trabajo sin transacción real. */
	private static final Transacciones SIN_TRANSACCION = new Transacciones() {

		@Override
		public <T> T ejecutar(Supplier<T> trabajo) {
			return trabajo.get();
		}

	};

	private final EquipoFalso equipo = new EquipoFalso();
	private final List<Map<String, Object>> auditados = new ArrayList<>();
	private final RegistroAuditoria auditoria = new RegistroAuditoria() {

		@Override
		public void registrar(UUID idUsuario, String accion, String entidad, String idEntidad, String ip,
				Instant fechaHora, Map<String, Object> detalle) {
			auditados.add(detalle);
		}

	};
	private final CambiarTelefonoPersonal cambiar = new CambiarTelefonoPersonal(equipo, auditoria, SIN_TRANSACCION,
			Clock.fixed(AHORA, ZoneOffset.UTC));

	/** El teléfono se guarda normalizado; con WhatsApp aceptado, el opt-in queda con la hora del cambio. */
	@Test
	void guardaElTelefonoYElOptIn() {
		cambiar.cambiar(CUENTA, "+56 9 1234 5678", true, ADMIN, "ip");

		assertThat(equipo.telefono).isEqualTo("+56912345678");
		assertThat(equipo.optIn).isEqualTo(AHORA);
		assertThat(auditados).hasSize(1);
		assertThat(auditados.get(0)).doesNotContainValue("+56912345678");
	}

	/** Sin WhatsApp aceptado, el opt-in queda vacío. */
	@Test
	void sinAceptarWhatsappElOptInQuedaVacio() {
		cambiar.cambiar(CUENTA, "+56 9 1234 5678", false, ADMIN, "ip");

		assertThat(equipo.telefono).isEqualTo("+56912345678");
		assertThat(equipo.optIn).isNull();
	}

	/** Una cuenta que no es coordinador puede quedar sin teléfono; un coordinador no. */
	@Test
	void soloElCoordinadorNecesitaTelefono() {
		equipo.cuenta = new CuentaEquipo(CUENTA, "PERSONAL", true, true);
		cambiar.cambiar(CUENTA, " ", false, ADMIN, "ip");
		assertThat(equipo.guardado).isTrue();
		assertThat(equipo.telefono).isNull();

		equipo.cuenta = new CuentaEquipo(CUENTA, "COORDINADOR", true, true);
		equipo.guardado = false;
		assertThatThrownBy(() -> cambiar.cambiar(CUENTA, null, false, ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThat(equipo.guardado).isFalse();
	}

	/** WhatsApp sin teléfono, o un número que no es un celular, se rechazan. */
	@Test
	void datosQueNoSirven() {
		equipo.cuenta = new CuentaEquipo(CUENTA, "PERSONAL", true, false);
		assertThatThrownBy(() -> cambiar.cambiar(CUENTA, "", true, ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> cambiar.cambiar(CUENTA, "123", false, ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThat(equipo.guardado).isFalse();
	}

	/** Una cuenta que no existe da 404. */
	@Test
	void unaCuentaQueNoExiste() {
		equipo.cuenta = null;

		assertThatThrownBy(() -> cambiar.cambiar(CUENTA, "+56 9 1234 5678", true, ADMIN, "ip"))
				.isInstanceOf(NoEncontrado.class);
	}

	/** Equipo con una sola cuenta de coordinador, que recuerda el último teléfono guardado. */
	private static final class EquipoFalso implements EquipoPersonal {

		private CuentaEquipo cuenta = new CuentaEquipo(CUENTA, "COORDINADOR", true, true);
		private boolean guardado;
		private String telefono;
		private Instant optIn;

		@Override
		public List<PerfilEquipo> listar() {
			return List.of();
		}

		@Override
		public Optional<CuentaEquipo> buscar(UUID id) {
			return Optional.ofNullable(cuenta);
		}

		@Override
		public boolean hayOtroAdministradorActivo(UUID excluida) {
			return true;
		}

		@Override
		public void cambiarRol(UUID id, String codigoRol) {
		}

		@Override
		public void guardarTelefono(UUID id, String telefono, Instant fechaOptIn) {
			guardado = true;
			this.telefono = telefono;
			this.optIn = fechaOptIn;
		}

		@Override
		public boolean cambiarRecibeAvisos(UUID id, boolean recibe) {
			return true;
		}

	}

}
