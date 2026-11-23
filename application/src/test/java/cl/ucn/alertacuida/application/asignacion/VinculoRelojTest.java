package cl.ucn.alertacuida.application.asignacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.soporte.AsignacionesEnMemoria;
import cl.ucn.alertacuida.application.soporte.EmparejamientosEnMemoria;
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

/**
 * Vincular, reemplazar, emparejar de nuevo y desvincular un reloj, siempre con el código de emparejamiento
 * que muestra el reloj, sin base de datos.
 */
class VinculoRelojTest {

	private static final Instant AHORA = Instant.parse("2026-10-22T15:00:00Z");
	private static final LocalDate AYER = LocalDate.of(2026, 10, 21);
	private static final UUID COORDINADOR = UUID.randomUUID();

	private final PacientesEnMemoria pacientes = new PacientesEnMemoria();
	private final RelojesEnMemoria relojes = new RelojesEnMemoria();
	private final AsignacionesEnMemoria asignaciones = new AsignacionesEnMemoria();
	private final EmparejamientosEnMemoria emparejamientos = new EmparejamientosEnMemoria();
	private VinculoReloj vinculo;
	private UUID maria;
	private UUID reloj1;
	private UUID reloj2;

	/** Un paciente con consentimiento y dos relojes operativos. El hash de un secreto es "hash-" + secreto. */
	@BeforeEach
	void preparar() {
		SecretosUnSoloUso secretos = new SecretosUnSoloUso() {
			@Override
			public String nuevo() {
				throw new UnsupportedOperationException("Vincular no inventa tokens: el reloj ya tiene el suyo");
			}

			@Override
			public String hash(String secreto) {
				return "hash-" + secreto;
			}
		};
		Transacciones enTransaccion = Supplier::get;
		vinculo = new VinculoReloj(asignaciones, pacientes, relojes, emparejamientos, secretos,
				(usuario, accion, entidad, id, ip, fecha, detalle) -> { }, enTransaccion,
				Clock.fixed(AHORA, ZoneOffset.UTC));
		maria = pacientes.crear(paciente(AYER), "h-1");
		reloj1 = relojes.agregar("R-001", EstadoReloj.OPERATIVO);
		reloj2 = relojes.agregar("R-002", EstadoReloj.OPERATIVO);
	}

	/** Al vincular, el vínculo queda con el token que ya tiene el reloj y el código se gasta. */
	@Test
	void vincularUsaElTokenDelRelojYElCodigoSirveUnaVez() {
		String codigo = codigoPendiente("K7PM42QX", "token-del-reloj");

		UUID idAsignacion = vinculo.vincular(maria, reloj1, "k7pm-42qx", COORDINADOR, "ip");

		assertThat(asignaciones.filas).singleElement().satisfies(fila -> {
			assertThat(fila.id).isEqualTo(idAsignacion);
			assertThat(fila.hashToken).isEqualTo("hash-token-del-reloj");
		});
		assertThat(emparejamientos.filas).isEmpty();
		vinculo.desvincular(maria, COORDINADOR, "ip");
		assertThatThrownBy(() -> vinculo.vincular(maria, reloj1, codigo, COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("venció");
	}

	/** Sin código no se vincula: nadie recibe ni escribe un token a mano. */
	@Test
	void sinCodigoNoSeVincula() {
		assertThatThrownBy(() -> vinculo.vincular(maria, reloj1, null, COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("código");
		assertThatThrownBy(() -> vinculo.vincular(maria, reloj1, "  ", COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThat(asignaciones.filas).isEmpty();
	}

	/** Sin consentimiento, dado de baja o con reloj no operativo, no se vincula. */
	@Test
	void sinConsentimientoDadoDeBajaORelojNoOperativoNoSeVincula() {
		UUID sinConsentimiento = pacientes.crear(paciente(null), "h-2");
		UUID dadoDeBaja = pacientes.crear(paciente(AYER), "h-3");
		pacientes.darDeBaja(dadoDeBaja, AYER);
		UUID enReparacion = relojes.agregar("R-003", EstadoReloj.EN_REPARACION);
		String codigo = codigoPendiente("AAAA2222", "t");

		assertThatThrownBy(() -> vinculo.vincular(sinConsentimiento, reloj1, codigo, COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("consentimiento");
		assertThatThrownBy(() -> vinculo.vincular(dadoDeBaja, reloj1, codigo, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("baja");
		assertThatThrownBy(() -> vinculo.vincular(maria, enReparacion, codigo, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("operativo");
		assertThatThrownBy(() -> vinculo.vincular(maria, UUID.randomUUID(), codigo, COORDINADOR, "ip"))
				.isInstanceOf(NoEncontrado.class);
		assertThat(emparejamientos.pendiente("hash-t", AHORA)).as("ningún intento fallido gasta el código").isTrue();
	}

	/** Un código vencido o inventado no vincula. */
	@Test
	void codigoVencidoOInventadoNoVincula() {
		emparejamientos.guardar("hash-VENCIDO2", "hash-a", AHORA.minusSeconds(700), AHORA.minusSeconds(100));

		assertThatThrownBy(() -> vinculo.vincular(maria, reloj1, "VENCIDO2", COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> vinculo.vincular(maria, reloj1, "ZZZZZZZZ", COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThat(asignaciones.filas).isEmpty();
	}

	/** Un reloj no puede estar con dos pacientes, ni un paciente con dos relojes. */
	@Test
	void unRelojConUnSoloPacienteYUnPacienteConUnSoloReloj() {
		UUID pedro = pacientes.crear(paciente(AYER), "h-2");
		vinculo.vincular(maria, reloj1, codigoPendiente("AAAA2222", "t1"), COORDINADOR, "ip");
		String otro = codigoPendiente("BBBB3333", "t2");

		assertThatThrownBy(() -> vinculo.vincular(pedro, reloj1, otro, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("otro usuario");
		assertThatThrownBy(() -> vinculo.vincular(maria, reloj2, otro, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("reemplazar");
	}

	/**
	 * El reemplazo cierra el reloj anterior en el mismo instante en que abre el nuevo, con el token del reloj
	 * nuevo, y conserva el historial. El token del anterior deja de servir.
	 */
	@Test
	void reemplazarCierraElAnteriorEnElMismoInstanteYUsaElTokenDelNuevo() {
		vinculo.vincular(maria, reloj1, codigoPendiente("AAAA2222", "token-viejo"), COORDINADOR, "ip");

		vinculo.reemplazar(maria, reloj2, EstadoReloj.EN_REPARACION, codigoPendiente("CCCC4444", "token-nuevo"),
				COORDINADOR, "ip");

		assertThat(asignaciones.filas).hasSize(2);
		assertThat(asignaciones.filas.get(0).fin).isEqualTo(AHORA);
		assertThat(asignaciones.filas.get(1).inicio).isEqualTo(AHORA);
		assertThat(asignaciones.vigentePorHashToken("hash-token-nuevo").orElseThrow().idDispositivo())
				.isEqualTo(reloj2);
		assertThat(asignaciones.vigentePorHashToken("hash-token-viejo")).isEmpty();
		assertThat(relojes.buscar(reloj1).orElseThrow().datos().estado()).isEqualTo(EstadoReloj.EN_REPARACION);
	}

	/** No se puede reemplazar si el paciente no tiene reloj, ni por el mismo reloj. */
	@Test
	void reemplazarPorElMismoRelojOSinRelojNoTieneSentido() {
		String codigo = codigoPendiente("BBBB3333", "t2");
		assertThatThrownBy(() -> vinculo.reemplazar(maria, reloj2, null, codigo, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class).hasMessageContaining("no tiene");
		vinculo.vincular(maria, reloj1, codigoPendiente("AAAA2222", "t1"), COORDINADOR, "ip");
		assertThatThrownBy(() -> vinculo.reemplazar(maria, reloj1, null, codigo, COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** Al desvincular, el reloj queda libre para otro paciente. */
	@Test
	void desvincularLiberaElRelojParaOtroPaciente() {
		UUID pedro = pacientes.crear(paciente(AYER), "h-2");
		vinculo.vincular(maria, reloj1, codigoPendiente("AAAA2222", "t1"), COORDINADOR, "ip");

		vinculo.desvincular(maria, COORDINADOR, "ip");
		vinculo.vincular(pedro, reloj1, codigoPendiente("BBBB3333", "t2"), COORDINADOR, "ip");

		assertThat(asignaciones.vigenteDelPaciente(maria)).isEmpty();
		assertThat(asignaciones.vigenteDelReloj(reloj1).orElseThrow().idPaciente()).isEqualTo(pedro);
	}

	/** Emparejar de nuevo cambia el token sin cerrar el vínculo; sin reloj vinculado no se puede. */
	@Test
	void emparejarDeNuevoCambiaElTokenSinCerrarElVinculo() {
		String restaurado = codigoPendiente("DDDD5555", "token-restaurado");
		assertThatThrownBy(() -> vinculo.emparejar(maria, restaurado, COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class);

		UUID idAsignacion = vinculo.vincular(maria, reloj1, codigoPendiente("AAAA2222", "t1"), COORDINADOR, "ip");
		assertThat(vinculo.emparejar(maria, restaurado, COORDINADOR, "ip")).isEqualTo(idAsignacion);

		assertThat(asignaciones.filas).singleElement().satisfies(fila -> {
			assertThat(fila.hashToken).isEqualTo("hash-token-restaurado");
			assertThat(fila.fin).isNull();
		});
		assertThatThrownBy(() -> vinculo.emparejar(maria, " ", COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/**
	 * Deja pendiente un código, como si un reloj lo hubiera pedido con ese token.
	 *
	 * @return el código, tal como lo escribiría quien instala
	 */
	private String codigoPendiente(String codigo, String tokenDelReloj) {
		emparejamientos.guardar("hash-" + codigo, "hash-" + tokenDelReloj, AHORA, AHORA.plusSeconds(600));
		return codigo;
	}

	/** @return datos de un paciente con esa fecha de consentimiento (null = sin consentimiento) */
	private static DatosPaciente paciente(LocalDate consentimiento) {
		return new DatosPaciente("12345678-5", "María", "Rojas", null, null, null, null, null, consentimiento, AYER);
	}

}
