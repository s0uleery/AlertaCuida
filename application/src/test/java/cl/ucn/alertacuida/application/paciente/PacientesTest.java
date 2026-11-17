package cl.ucn.alertacuida.application.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.paciente.modelo.ResumenPaciente;
import cl.ucn.alertacuida.application.soporte.AsignacionesEnMemoria;
import cl.ucn.alertacuida.application.soporte.PacientesEnMemoria;
import cl.ucn.alertacuida.domain.paciente.NivelDependencia;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Alta, edición, baja y consulta de pacientes, sin base de datos. */
class PacientesTest {

	/** 12:00 en Chile del 22-10-2026. */
	private static final Instant AHORA = Instant.parse("2026-10-22T15:00:00Z");
	private static final LocalDate HOY = LocalDate.of(2026, 10, 22);
	private static final UUID COORDINADOR = UUID.randomUUID();

	private final PacientesEnMemoria pacientes = new PacientesEnMemoria();
	private final AsignacionesEnMemoria asignaciones = new AsignacionesEnMemoria();
	private final List<String> auditoria = new ArrayList<>();
	private RegistrarPaciente registrar;
	private EditarPaciente editar;
	private DarDeBajaPaciente baja;
	private ConsultarPacientes consultar;

	/** Casos de uso con dobles en memoria y la hora fija en {@code AHORA}. */
	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
		RegistroAuditoria registro = (usuario, accion, entidad, id, ip, fecha, detalle) ->
				auditoria.add(accion + " " + entidad);
		Transacciones enTransaccion = Supplier::get;
		// El "hash" del RUN es "h-" + RUN normalizado
		registrar = new RegistrarPaciente(pacientes, run -> "h-" + run.valor(), registro, enTransaccion, reloj);
		editar = new EditarPaciente(pacientes, asignaciones, run -> "h-" + run.valor(), registro, enTransaccion, reloj);
		baja = new DarDeBajaPaciente(pacientes, asignaciones, registro, enTransaccion, reloj);
		consultar = new ConsultarPacientes(pacientes, asignaciones, reloj);
	}

	/** El alta guarda el RUN sin puntos y los textos sin espacios sobrantes (vacío = null). */
	@Test
	void registraConElRunNormalizadoYLosTextosLimpios() {
		UUID id = registrar.registrar(datos("12.345.678-5", "  María José ", LocalDate.of(1940, 10, 23)), COORDINADOR,
				"ip");

		DatosPaciente guardado = pacientes.buscar(id).orElseThrow().datos();
		assertThat(guardado.run()).isEqualTo("12345678-5");
		assertThat(guardado.nombres()).isEqualTo("María José");
		assertThat(guardado.alias()).isNull();
		assertThat(auditoria).containsExactly("CREAR PACIENTE");
	}

	/** La edad se calcula con la fecha de hoy en Chile: la víspera del cumpleaños aún no lo cumple. */
	@Test
	void laEdadSeCalculaHoyEnChile() {
		// Cumple 86 mañana: hoy todavía tiene 85
		registrar.registrar(datos("12.345.678-5", "María", LocalDate.of(1940, 10, 23)), COORDINADOR, "ip");

		ResumenPaciente fila = consultar.listar(false).getFirst();
		assertThat(fila.edad()).isEqualTo(85);
		assertThat(fila.relojActual()).isNull();
	}

	/** Se rechazan el RUN repetido o inválido, las fechas futuras y los nombres vacíos. */
	@Test
	void runRepetidoInvalidoOFechasFuturasSeRechazan() {
		registrar.registrar(datos("12.345.678-5", "María", null), COORDINADOR, "ip");

		assertThatThrownBy(() -> registrar.registrar(datos("12345678-5", "Otra", null), COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class);
		assertThatThrownBy(() -> registrar.registrar(datos("12.345.678-0", "Otra", null), COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> registrar.registrar(datos("11.111.111-1", "Otra", HOY.plusDays(1)), COORDINADOR,
				"ip")).isInstanceOf(SolicitudRechazada.class).hasMessageContaining("nacimiento");
		assertThatThrownBy(() -> registrar.registrar(datos("11.111.111-1", " ", null), COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("nombres");
	}

	/** Al editar se puede corregir el RUN, pero no usar el de otro paciente. */
	@Test
	void editarPermiteCorregirElRunPeroNoUsarElDeOtro() {
		UUID maria = registrar.registrar(datos("12.345.678-5", "María", null), COORDINADOR, "ip");
		registrar.registrar(datos("11.111.111-1", "Pedro", null), COORDINADOR, "ip");

		editar.editar(maria, datos("22.222.222-2", "María", null), COORDINADOR, "ip");
		assertThat(pacientes.buscar(maria).orElseThrow().datos().run()).isEqualTo("22222222-2");

		assertThatThrownBy(() -> editar.editar(maria, datos("11.111.111-1", "María", null), COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class);
		assertThatThrownBy(() -> editar.editar(UUID.randomUUID(), datos("33.333.333-3", "X", null), COORDINADOR,
				"ip")).isInstanceOf(NoEncontrado.class);
	}

	/** No se puede quitar el consentimiento a quien tiene reloj. */
	@Test
	void conRelojNoSePuedeQuitarElConsentimiento() {
		UUID maria = registrar.registrar(datos("12.345.678-5", "María", null), COORDINADOR, "ip");
		asignaciones.abrir(UUID.randomUUID(), maria, AHORA, "hash");
		DatosPaciente sinConsentimiento = new DatosPaciente("12.345.678-5", "María", "Rojas", null, null, null, null,
				null, null, HOY);

		assertThatThrownBy(() -> editar.editar(maria, sinConsentimiento, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("consentimiento");
	}

	/** La baja cierra el reloj y saca al paciente de la lista, pero conserva su ficha e historial. */
	@Test
	void laBajaCierraElRelojYSaleDeLaListaPeroSeConserva() {
		UUID maria = registrar.registrar(datos("12.345.678-5", "María", null), COORDINADOR, "ip");
		asignaciones.abrir(UUID.randomUUID(), maria, AHORA.minusSeconds(3600), "hash");

		baja.darDeBaja(maria, COORDINADOR, "ip");

		assertThat(pacientes.buscar(maria).orElseThrow().fechaBaja()).isEqualTo(HOY);
		assertThat(asignaciones.filas.getFirst().fin).isEqualTo(AHORA);
		assertThat(consultar.listar(false)).isEmpty();
		assertThat(consultar.listar(true)).hasSize(1);
		assertThat(consultar.detalle(maria).historial()).hasSize(1);
		assertThatThrownBy(() -> baja.darDeBaja(maria, COORDINADOR, "ip")).isInstanceOf(Conflicto.class);
	}

	/**
	 * @return datos válidos de un paciente con ese RUN, nombres y fecha de nacimiento; consentimiento e
	 *         ingreso de ayer
	 */
	private static DatosPaciente datos(String run, String nombres, LocalDate nacimiento) {
		return new DatosPaciente(run, nombres, "Rojas", " ", nacimiento, null, null, NivelDependencia.SEVERA,
				HOY.minusDays(1), HOY.minusDays(1));
	}

}
