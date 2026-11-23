package cl.ucn.alertacuida.application.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.modelo.CambioRol;
import cl.ucn.alertacuida.application.personal.modelo.CuentaEquipo;
import cl.ucn.alertacuida.application.personal.modelo.PerfilEquipo;
import cl.ucn.alertacuida.application.personal.modelo.RolConPermisos;
import cl.ucn.alertacuida.application.personal.puerto.EquipoPersonal;
import cl.ucn.alertacuida.application.personal.puerto.RolesPermisos;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Administración del equipo ya creado: cambio de rol, aviso técnico por correo y permisos de
 * cada rol. Las reglas salen de las decisiones del 13-11-2026 (docs/backend.md), sin base de datos.
 */
class EquipoYPermisosTest {

	private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
	private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OTRO_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private static final UUID COORDINADORA = UUID.fromString("00000000-0000-0000-0000-000000000004");

	private final EquipoEnMemoria equipo = new EquipoEnMemoria();
	private final RolesEnMemoria roles = new RolesEnMemoria();
	private final List<String> auditoria = new ArrayList<>();
	private final List<Map<String, Object>> detalles = new ArrayList<>();
	private CambiarRolPersonal cambiarRol;
	private CambiarRecibeAvisos cambiarAvisos;
	private GuardarPermisosRol guardarPermisos;

	/** Un administrador activo, una persona sin teléfono y una coordinadora con teléfono. */
	@BeforeEach
	void preparar() {
		RegistroAuditoria registro = (usuario, accion, entidad, id, ip, fecha, detalle) -> {
			auditoria.add(accion + " " + entidad + " " + id + " por " + usuario);
			detalles.add(detalle);
		};
		Transacciones enTransaccion = Supplier::get;
		Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
		cambiarRol = new CambiarRolPersonal(equipo, roles, registro, enTransaccion, reloj);
		cambiarAvisos = new CambiarRecibeAvisos(equipo, registro, reloj);
		guardarPermisos = new GuardarPermisosRol(roles, registro, enTransaccion, reloj);

		roles.guardar("ADMINISTRADOR", true, "ADMINISTRACION:VER", "ADMINISTRACION:EDITAR");
		roles.guardar("COORDINADOR", false, "ALERTAS:VER", "USUARIOS:VER");
		roles.guardar("PERSONAL", false, "ALERTAS:VER", "ALERTAS:EDITAR");
		equipo.agregar(new CuentaEquipo(ADMIN, "ADMINISTRADOR", true, false));
		equipo.agregar(new CuentaEquipo(ANA, "PERSONAL", true, false));
		equipo.agregar(new CuentaEquipo(COORDINADORA, "COORDINADOR", true, true));
	}

	/** El cambio de rol se guarda y la auditoría lleva el rol anterior y el nuevo. */
	@Test
	void elCambioDeRolSeGuardaYSeAuditaConElRolAnteriorYElNuevo() {
		cambiarRol.cambiar(COORDINADORA, new CambioRol("PERSONAL", null, false), ADMIN, "ip");

		assertThat(equipo.cuentas.get(COORDINADORA).codigoRol()).isEqualTo("PERSONAL");
		assertThat(auditoria).containsExactly("MODIFICAR USUARIO " + COORDINADORA + " por " + ADMIN);
		assertThat(detalles.getFirst()).containsEntry("rolAnterior", "COORDINADOR")
				.containsEntry("rolNuevo", "PERSONAL");
	}

	/** Nadie cambia su propio rol: así no se quita el acceso por error. */
	@Test
	void nadieCambiaSuPropioRol() {
		assertThatThrownBy(() -> cambiarRol.cambiar(ADMIN, new CambioRol("PERSONAL", null, false), ADMIN, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("propio");
		assertThat(equipo.cuentas.get(ADMIN).codigoRol()).isEqualTo("ADMINISTRADOR");
	}

	/** No se puede dejar el sistema sin ningún administrador activo; con otro, sí se puede. */
	@Test
	void noSePuedeDejarSinAdministradorActivo() {
		// ANA tiene permiso de editar (se lo dieron a su rol) e intenta bajar al único administrador
		assertThatThrownBy(() -> cambiarRol.cambiar(ADMIN, new CambioRol("PERSONAL", null, false), ANA, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("administrador");

		equipo.agregar(new CuentaEquipo(OTRO_ADMIN, "ADMINISTRADOR", true, false));
		cambiarRol.cambiar(ADMIN, new CambioRol("PERSONAL", null, false), OTRO_ADMIN, "ip");
		assertThat(equipo.cuentas.get(ADMIN).codigoRol()).isEqualTo("PERSONAL");
	}

	/** Un administrador pendiente de activar no cuenta como administrador activo. */
	@Test
	void unAdministradorPendienteNoCuentaComoActivo() {
		equipo.agregar(new CuentaEquipo(OTRO_ADMIN, "ADMINISTRADOR", false, false));

		assertThatThrownBy(() -> cambiarRol.cambiar(ADMIN, new CambioRol("PERSONAL", null, false), ANA, "ip"))
				.isInstanceOf(Conflicto.class);
	}

	/** Pasar a coordinador sin teléfono se rechaza; con teléfono se guarda normalizado y con su opt-in. */
	@Test
	void pasarACoordinadorExigeTelefonoSiNoLoTiene() {
		assertThatThrownBy(() -> cambiarRol.cambiar(ANA, new CambioRol("COORDINADOR", " ", false), ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("WhatsApp");
		assertThat(equipo.cuentas.get(ANA).codigoRol()).isEqualTo("PERSONAL");

		cambiarRol.cambiar(ANA, new CambioRol("COORDINADOR", "9 1234 5678", true), ADMIN, "ip");

		assertThat(equipo.cuentas.get(ANA).codigoRol()).isEqualTo("COORDINADOR");
		assertThat(equipo.telefonos.get(ANA)).isEqualTo("+56912345678");
		assertThat(equipo.optIns.get(ANA)).isEqualTo(AHORA);
		assertThat(detalles.getFirst()).containsEntry("telefonoAgregado", true);
	}

	/** Quien ya tiene teléfono pasa a coordinador sin escribirlo de nuevo, y su teléfono no se toca. */
	@Test
	void quienYaTieneTelefonoPasaACoordinadorSinEscribirlo() {
		cambiarRol.cambiar(COORDINADORA, new CambioRol("PERSONAL", null, false), ADMIN, "ip");
		cambiarRol.cambiar(COORDINADORA, new CambioRol("COORDINADOR", null, false), ADMIN, "ip");

		assertThat(equipo.cuentas.get(COORDINADORA).codigoRol()).isEqualTo("COORDINADOR");
		assertThat(equipo.telefonos).doesNotContainKey(COORDINADORA);
	}

	/** Un teléfono inválido o aceptar WhatsApp sin teléfono se rechazan, igual que en el alta. */
	@Test
	void elTelefonoSeValidaIgualQueEnElAlta() {
		assertThatThrownBy(() -> cambiarRol.cambiar(ANA, new CambioRol("COORDINADOR", "123", true), ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> cambiarRol.cambiar(COORDINADORA, new CambioRol("PERSONAL", null, true), ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("teléfono");
	}

	/** Un rol que no existe responde 400, una cuenta que no existe 404; el mismo rol no cambia nada. */
	@Test
	void rolInexistenteCuentaInexistenteYMismoRol() {
		assertThatThrownBy(() -> cambiarRol.cambiar(ANA, new CambioRol("JEFE", null, false), ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("rol");
		assertThatThrownBy(() -> cambiarRol.cambiar(UUID.randomUUID(), new CambioRol("PERSONAL", null, false), ADMIN,
				"ip")).isInstanceOf(NoEncontrado.class);

		cambiarRol.cambiar(ANA, new CambioRol("PERSONAL", null, false), ADMIN, "ip");
		assertThat(auditoria).isEmpty();
	}

	/** El aviso técnico por correo se enciende y apaga, también en la cuenta propia, y queda auditado. */
	@Test
	void elAvisoTecnicoSeEnciendeYApagaYSeAudita() {
		cambiarAvisos.cambiar(ANA, true, ADMIN, "ip");
		assertThat(equipo.recibeAvisos).containsEntry(ANA, true);
		cambiarAvisos.cambiar(ADMIN, true, ADMIN, "ip");
		cambiarAvisos.cambiar(ANA, false, ADMIN, "ip");

		assertThat(equipo.recibeAvisos).containsEntry(ANA, false).containsEntry(ADMIN, true);
		assertThat(auditoria).hasSize(3);
		assertThat(detalles.getLast()).containsEntry("recibeAvisos", false);
		assertThatThrownBy(() -> cambiarAvisos.cambiar(UUID.randomUUID(), true, ADMIN, "ip"))
				.isInstanceOf(NoEncontrado.class);
	}

	/** Los permisos del rol se reemplazan y la auditoría dice cuáles se agregaron y cuáles se quitaron. */
	@Test
	void losPermisosSeReemplazanYSeAuditaLoAgregadoYLoQuitado() {
		guardarPermisos.guardar("PERSONAL", Set.of("ALERTAS:VER", "REPORTES:VER"), ADMIN, "ip");

		assertThat(roles.roles.get("PERSONAL").permisos()).containsExactlyInAnyOrder("ALERTAS:VER", "REPORTES:VER");
		assertThat(auditoria).containsExactly("MODIFICAR PERMISO_ROL PERSONAL por " + ADMIN);
		assertThat(detalles.getFirst()).containsEntry("agregados", List.of("REPORTES:VER"))
				.containsEntry("quitados", List.of("ALERTAS:EDITAR"));
	}

	/** Los permisos del administrador no se editan, para no dejar a nadie sin acceso. */
	@Test
	void losPermisosDelAdministradorNoSeEditan() {
		assertThatThrownBy(() -> guardarPermisos.guardar("ADMINISTRADOR", Set.of("ALERTAS:VER"), ADMIN, "ip"))
				.isInstanceOf(Conflicto.class);
		assertThat(roles.roles.get("ADMINISTRADOR").permisos()).contains("ADMINISTRACION:EDITAR");
	}

	/** Un permiso mal escrito responde 400 y un rol que no existe 404; sin cambios no se audita. */
	@Test
	void permisoInvalidoRolInexistenteYSinCambios() {
		assertThatThrownBy(() -> guardarPermisos.guardar("PERSONAL", Set.of("ALERTAS:BORRAR"), ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("ALERTAS:BORRAR");
		assertThatThrownBy(() -> guardarPermisos.guardar("PERSONAL", Set.of("PACIENTES:VER"), ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> guardarPermisos.guardar("JEFE", Set.of("ALERTAS:VER"), ADMIN, "ip"))
				.isInstanceOf(NoEncontrado.class);

		guardarPermisos.guardar("PERSONAL", Set.of("ALERTAS:VER", "ALERTAS:EDITAR"), ADMIN, "ip");
		assertThat(auditoria).isEmpty();
	}

	/** Cuentas del equipo en memoria, con lo que guardan los casos de uso. */
	private static final class EquipoEnMemoria implements EquipoPersonal {

		private final Map<UUID, CuentaEquipo> cuentas = new LinkedHashMap<>();
		private final Map<UUID, String> telefonos = new HashMap<>();
		private final Map<UUID, Instant> optIns = new HashMap<>();
		private final Map<UUID, Boolean> recibeAvisos = new HashMap<>();

		void agregar(CuentaEquipo cuenta) {
			cuentas.put(cuenta.id(), cuenta);
		}

		@Override
		public List<PerfilEquipo> listar() {
			return List.of();
		}

		@Override
		public Optional<CuentaEquipo> buscar(UUID id) {
			return Optional.ofNullable(cuentas.get(id));
		}

		@Override
		public boolean hayOtroAdministradorActivo(UUID excluida) {
			return cuentas.values().stream()
					.anyMatch(c -> !c.id().equals(excluida) && c.activa() && "ADMINISTRADOR".equals(c.codigoRol()));
		}

		@Override
		public void cambiarRol(UUID id, String codigoRol) {
			CuentaEquipo antes = cuentas.get(id);
			cuentas.put(id, new CuentaEquipo(id, codigoRol, antes.activa(), antes.tieneTelefono()));
		}

		@Override
		public void guardarTelefono(UUID id, String telefono, Instant fechaOptIn) {
			telefonos.put(id, telefono);
			optIns.put(id, fechaOptIn);
		}

		@Override
		public boolean cambiarRecibeAvisos(UUID id, boolean recibe) {
			if (!cuentas.containsKey(id)) {
				return false;
			}
			recibeAvisos.put(id, recibe);
			return true;
		}

	}

	/** Roles activos en memoria; el de sistema es el administrador. */
	private static final class RolesEnMemoria implements RolesPermisos {

		private final Map<String, RolConPermisos> roles = new LinkedHashMap<>();

		void guardar(String codigo, boolean esSistema, String... permisos) {
			roles.put(codigo, new RolConPermisos(codigo, codigo, esSistema, new TreeSet<>(Set.of(permisos))));
		}

		@Override
		public List<RolConPermisos> listar() {
			return List.copyOf(roles.values());
		}

		@Override
		public Optional<RolConPermisos> buscar(String codigo) {
			return Optional.ofNullable(roles.get(codigo));
		}

		@Override
		public void reemplazarPermisos(String codigo, Set<String> permisos) {
			RolConPermisos antes = roles.get(codigo);
			roles.put(codigo, new RolConPermisos(codigo, antes.nombre(), antes.esSistema(), new TreeSet<>(permisos)));
		}

	}

}
