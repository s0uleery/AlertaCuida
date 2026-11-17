package cl.ucn.alertacuida.application.condicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.condicion.modelo.Condicion;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.soporte.CondicionesEnMemoria;
import cl.ucn.alertacuida.application.soporte.PacientesEnMemoria;
import cl.ucn.alertacuida.domain.paciente.TipoCondicion;
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

/** Enfermedades y situaciones de un paciente, sin base de datos. */
class CondicionesPacienteTest {

	private static final UUID COORDINADOR = UUID.randomUUID();

	private final PacientesEnMemoria pacientes = new PacientesEnMemoria();
	private final CondicionesEnMemoria guardadas = new CondicionesEnMemoria();
	private final List<String> auditoria = new ArrayList<>();
	private CondicionesPaciente condiciones;
	private UUID maria;

	/** El caso de uso con dobles en memoria y una paciente ya registrada. */
	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneOffset.UTC);
		RegistroAuditoria registro = (usuario, accion, entidad, id, ip, fecha, detalle) ->
				auditoria.add(accion + " " + entidad);
		Transacciones enTransaccion = Supplier::get;
		condiciones = new CondicionesPaciente(guardadas, pacientes, registro, enTransaccion, reloj);
		maria = pacientes.crear(new DatosPaciente("12345678-5", "María", "Rojas", null, null, null, null, null, null,
				LocalDate.of(2026, 1, 10)), "h");
	}

	/** Lista primero las enfermedades y luego las situaciones, cada grupo por orden alfabético. */
	@Test
	void listaEnfermedadesYLuegoSituacionesEnOrdenAlfabetico() {
		condiciones.agregar(maria, TipoCondicion.SITUACION, "Vive sola", COORDINADOR, "ip");
		condiciones.agregar(maria, TipoCondicion.ENFERMEDAD, "Hipertensión", COORDINADOR, "ip");
		condiciones.agregar(maria, TipoCondicion.ENFERMEDAD, "diabetes", COORDINADOR, "ip");

		assertThat(condiciones.listar(maria)).extracting(Condicion::descripcion)
				.containsExactly("diabetes", "Hipertensión", "Vive sola");
		assertThat(auditoria).containsOnly("CREAR CONDICION_PACIENTE").hasSize(3);
	}

	/** La descripción se guarda sin espacios sobrantes; vacía o muy larga se rechaza. */
	@Test
	void limpiaLaDescripcionYRechazaVaciaOMuyLarga() {
		condiciones.agregar(maria, TipoCondicion.ENFERMEDAD, "  Insuficiencia   cardíaca ", COORDINADOR, "ip");

		assertThat(condiciones.listar(maria).getFirst().descripcion()).isEqualTo("Insuficiencia cardíaca");
		assertThatThrownBy(() -> condiciones.agregar(maria, TipoCondicion.ENFERMEDAD, "   ", COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> condiciones.agregar(maria, TipoCondicion.ENFERMEDAD, "x".repeat(201), COORDINADOR,
				"ip")).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> condiciones.agregar(maria, null, "EPOC", COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** La misma condición no se repite, aunque cambien las mayúsculas; con otro tipo sí se acepta. */
	@Test
	void noSeRepiteLaMismaCondicion() {
		condiciones.agregar(maria, TipoCondicion.ENFERMEDAD, "EPOC", COORDINADOR, "ip");

		assertThatThrownBy(() -> condiciones.agregar(maria, TipoCondicion.ENFERMEDAD, "epoc", COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class);
		condiciones.agregar(maria, TipoCondicion.SITUACION, "EPOC", COORDINADOR, "ip");
		assertThat(condiciones.listar(maria)).hasSize(2);
	}

	/** Quitar borra solo una condición del mismo paciente; la de otro paciente responde "no encontrado". */
	@Test
	void quitarSoloBorraCondicionesDelMismoPaciente() {
		UUID pedro = pacientes.crear(new DatosPaciente("9999999-3", "Pedro", "Soto", null, null, null, null, null,
				null, LocalDate.of(2026, 1, 10)), "h2");
		UUID epoc = condiciones.agregar(maria, TipoCondicion.ENFERMEDAD, "EPOC", COORDINADOR, "ip");

		assertThatThrownBy(() -> condiciones.quitar(pedro, epoc, COORDINADOR, "ip")).isInstanceOf(NoEncontrado.class);
		condiciones.quitar(maria, epoc, COORDINADOR, "ip");

		assertThat(condiciones.listar(maria)).isEmpty();
		assertThat(auditoria).containsExactly("CREAR CONDICION_PACIENTE", "ELIMINAR CONDICION_PACIENTE");
	}

	/** Con un paciente que no existe, todo responde "no encontrado". */
	@Test
	void pacienteInexistenteRespondeNoEncontrado() {
		UUID nadie = UUID.randomUUID();

		assertThatThrownBy(() -> condiciones.listar(nadie)).isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> condiciones.agregar(nadie, TipoCondicion.ENFERMEDAD, "EPOC", COORDINADOR, "ip"))
				.isInstanceOf(NoEncontrado.class);
	}

}
