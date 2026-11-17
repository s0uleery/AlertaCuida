package cl.ucn.alertacuida.application.asignacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.asignacion.modelo.TokenReloj;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.soporte.AsignacionesEnMemoria;
import cl.ucn.alertacuida.application.soporte.PacientesEnMemoria;
import cl.ucn.alertacuida.application.soporte.RelojesEnMemoria;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Vincular, reemplazar, desvincular y renovar el token de un reloj, sin base de datos. */
class VinculoRelojTest {

	private static final Instant AHORA = Instant.parse("2026-10-22T15:00:00Z");
	private static final LocalDate AYER = LocalDate.of(2026, 10, 21);
	private static final UUID COORDINADOR = UUID.randomUUID();

	private final PacientesEnMemoria pacientes = new PacientesEnMemoria();
	private final RelojesEnMemoria relojes = new RelojesEnMemoria();
	private final AsignacionesEnMemoria asignaciones = new AsignacionesEnMemoria();
	private VinculoReloj vinculo;
	private UUID maria;
	private UUID reloj1;
	private UUID reloj2;

	/**
	 * Un paciente con consentimiento y dos relojes operativos. Los tokens son "token-1", "token-2"…
	 * y su hash, "hash-" + token.
	 */
	@BeforeEach
	void preparar() {
		SecretosUnSoloUso secretos = new SecretosUnSoloUso() {
			private int emitidos;

			@Override
			public String nuevo() {
				return "token-" + ++emitidos;
			}

			@Override
			public String hash(String secreto) {
				return "hash-" + secreto;
			}
		};
		Transacciones enTransaccion = Supplier::get;
		vinculo = new VinculoReloj(asignaciones, pacientes, relojes, secretos,
				(usuario, accion, entidad, id, ip, fecha, detalle) -> { }, enTransaccion,
				Clock.fixed(AHORA, ZoneOffset.UTC));
		maria = pacientes.crear(paciente(AYER), "h-1");
		reloj1 = relojes.agregar("R-001", EstadoReloj.OPERATIVO);
		reloj2 = relojes.agregar("R-002", EstadoReloj.OPERATIVO);
	}

	/** Al vincular se entrega el token una vez y se guarda solo su hash. */
	@Test
	void vincularEntregaUnTokenYGuardaSoloSuHash() {
		TokenReloj token = vinculo.vincular(maria, reloj1, COORDINADOR, "ip");

		assertThat(token.token()).isEqualTo("token-1");
		assertThat(asignaciones.filas.getFirst().hashToken).isEqualTo("hash-token-1");
		assertThat(asignaciones.vigenteDelPaciente(maria)).isPresent();
	}

	/** Sin consentimiento, dado de baja o con reloj no operativo, no se vincula. */
	@Test
	void sinConsentimientoDadoDeBajaORelojNoOperativoNoSeVincula() {
		UUID sinConsentimiento = pacientes.crear(paciente(null), "h-2");
		UUID dadoDeBaja = pacientes.crear(paciente(AYER), "h-3");
		pacientes.darDeBaja(dadoDeBaja, AYER);
		UUID enReparacion = relojes.agregar("R-003", EstadoReloj.EN_REPARACION);

		assertThatThrownBy(() -> vinculo.vincular(sinConsentimiento, reloj1, COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("consentimiento");
		assertThatThrownBy(() -> vinculo.vincular(dadoDeBaja, reloj1, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("baja");
		assertThatThrownBy(() -> vinculo.vincular(maria, enReparacion, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("operativo");
		assertThatThrownBy(() -> vinculo.vincular(maria, UUID.randomUUID(), COORDINADOR, "ip"))
				.isInstanceOf(NoEncontrado.class);
	}

	/** Un reloj no puede estar con dos pacientes, ni un paciente con dos relojes. */
	@Test
	void unRelojConUnSoloPacienteYUnPacienteConUnSoloReloj() {
		UUID pedro = pacientes.crear(paciente(AYER), "h-2");
		vinculo.vincular(maria, reloj1, COORDINADOR, "ip");

		assertThatThrownBy(() -> vinculo.vincular(pedro, reloj1, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("otro usuario");
		assertThatThrownBy(() -> vinculo.vincular(maria, reloj2, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("reemplazar");
	}

	/** El reemplazo cierra el reloj anterior en el mismo instante en que abre el nuevo, y conserva el historial. */
	@Test
	void reemplazarCierraElAnteriorEnElMismoInstanteYConservaElHistorial() {
		vinculo.vincular(maria, reloj1, COORDINADOR, "ip");

		TokenReloj nuevo = vinculo.reemplazar(maria, reloj2, EstadoReloj.EN_REPARACION, COORDINADOR, "ip");

		assertThat(asignaciones.filas).hasSize(2);
		assertThat(asignaciones.filas.get(0).fin).isEqualTo(AHORA);
		assertThat(asignaciones.filas.get(1).inicio).isEqualTo(AHORA);
		assertThat(asignaciones.vigenteDelPaciente(maria).orElseThrow().idDispositivo()).isEqualTo(reloj2);
		assertThat(relojes.buscar(reloj1).orElseThrow().datos().estado()).isEqualTo(EstadoReloj.EN_REPARACION);
		assertThat(nuevo.token()).isEqualTo("token-2");
	}

	/** No se puede reemplazar si el paciente no tiene reloj, ni por el mismo reloj. */
	@Test
	void reemplazarPorElMismoRelojOSinRelojNoTieneSentido() {
		assertThatThrownBy(() -> vinculo.reemplazar(maria, reloj2, null, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("no tiene");
		vinculo.vincular(maria, reloj1, COORDINADOR, "ip");
		assertThatThrownBy(() -> vinculo.reemplazar(maria, reloj1, null, COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** Al desvincular, el reloj queda libre para otro paciente. */
	@Test
	void desvincularLiberaElRelojParaOtroPaciente() {
		UUID pedro = pacientes.crear(paciente(AYER), "h-2");
		vinculo.vincular(maria, reloj1, COORDINADOR, "ip");

		vinculo.desvincular(maria, COORDINADOR, "ip");
		vinculo.vincular(pedro, reloj1, COORDINADOR, "ip");

		assertThat(asignaciones.vigenteDelPaciente(maria)).isEmpty();
		assertThat(asignaciones.vigenteDelReloj(reloj1).orElseThrow().idPaciente()).isEqualTo(pedro);
	}

	/** Un token nuevo reemplaza al anterior sin cerrar el vínculo. */
	@Test
	void unTokenNuevoReemplazaAlAnteriorSinCerrarElVinculo() {
		vinculo.vincular(maria, reloj1, COORDINADOR, "ip");

		TokenReloj nuevo = vinculo.nuevoToken(maria, COORDINADOR, "ip");

		assertThat(asignaciones.filas).hasSize(1);
		assertThat(asignaciones.filas.getFirst().hashToken).isEqualTo("hash-" + nuevo.token());
		assertThat(asignaciones.filas.getFirst().fin).isNull();
	}

	/** @return datos de un paciente con esa fecha de consentimiento (null = sin consentimiento) */
	private static DatosPaciente paciente(LocalDate consentimiento) {
		return new DatosPaciente("12345678-5", "María", "Rojas", null, null, null, null, null, consentimiento, AYER);
	}

}
