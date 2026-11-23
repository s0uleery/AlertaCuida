package cl.ucn.alertacuida.application.asignacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.asignacion.error.DemasiadosEmparejamientos;
import cl.ucn.alertacuida.application.asignacion.modelo.CodigoEmparejamiento;
import cl.ucn.alertacuida.application.asignacion.modelo.EstadoEmparejamiento;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.soporte.AsignacionesEnMemoria;
import cl.ucn.alertacuida.application.soporte.EmparejamientosEnMemoria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El lado del reloj del emparejamiento: pedir un código y preguntar si ya lo vincularon. */
class EmparejarRelojTest {

	private static final Instant AHORA = Instant.parse("2026-11-23T15:00:00Z");
	private static final Duration VIGENCIA = Duration.ofMinutes(10);

	private final EmparejamientosEnMemoria emparejamientos = new EmparejamientosEnMemoria();
	private final AsignacionesEnMemoria asignaciones = new AsignacionesEnMemoria();
	private final Deque<String> codigos = new ArrayDeque<>();
	private Instant ahora = AHORA;
	private EmparejarReloj emparejar;

	/** Tokens "token-1", "token-2"… con hash "hash-" + token; los códigos salen de la cola. */
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
		Clock reloj = new Clock() {
			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zona) {
				return this;
			}

			@Override
			public Instant instant() {
				return ahora;
			}
		};
		emparejar = new EmparejarReloj(emparejamientos, asignaciones, codigos::pop, secretos, VIGENCIA, 2, 5, reloj);
	}

	/** El reloj recibe el código con guion y su token; la base guarda solo hashes. */
	@Test
	void entregaElCodigoConGuionYElTokenYGuardaSoloHashes() {
		codigos.add("K7PM42QX");

		CodigoEmparejamiento codigo = emparejar.iniciar();

		assertThat(codigo.codigo()).isEqualTo("K7PM-42QX");
		assertThat(codigo.token()).isEqualTo("token-1");
		assertThat(codigo.expiraEn()).isEqualTo(AHORA.plus(VIGENCIA));
		assertThat(codigo.segundosConsulta()).isEqualTo(5);
		assertThat(emparejamientos.filas).singleElement().satisfies(fila -> {
			assertThat(fila.hashCodigo()).isEqualTo("hash-K7PM42QX");
			assertThat(fila.hashToken()).isEqualTo("hash-token-1");
		});
		assertThat(codigo.toString()).doesNotContain("token-1");
	}

	/** Si sale un código que ya está pendiente, se sortea otro. */
	@Test
	void siElCodigoYaEstaPendienteSorteaOtro() {
		codigos.addAll(List.of("AAAA2222", "AAAA2222", "BBBB3333"));
		emparejar.iniciar();

		assertThat(emparejar.iniciar().codigo()).isEqualTo("BBBB-3333");
	}

	/** Con demasiados pendientes responde 429; los vencidos no cuentan y se borran. */
	@Test
	void conDemasiadosPendientesRechazaYLosVencidosSeBorran() {
		codigos.addAll(List.of("AAAA2222", "BBBB3333", "CCCC4444"));
		emparejar.iniciar();
		emparejar.iniciar();

		assertThatThrownBy(emparejar::iniciar).isInstanceOf(DemasiadosEmparejamientos.class);

		ahora = AHORA.plus(VIGENCIA);
		emparejar.iniciar();
		assertThat(emparejamientos.filas).singleElement()
				.satisfies(fila -> assertThat(fila.hashCodigo()).isEqualTo("hash-CCCC4444"));
	}

	/** PENDIENTE mientras nadie escribe el código, VINCULADO cuando su token sirve, VENCIDO si no. */
	@Test
	void laConsultaDistinguePendienteVinculadoYVencido() {
		codigos.addAll(List.of("AAAA2222", "BBBB3333"));
		String pendiente = emparejar.iniciar().token();
		String vinculado = emparejar.iniciar().token();
		emparejamientos.usar("hash-BBBB3333", AHORA);
		asignaciones.abrir(UUID.randomUUID(), UUID.randomUUID(), AHORA, "hash-" + vinculado);

		assertThat(emparejar.consultar(pendiente)).isEqualTo(EstadoEmparejamiento.PENDIENTE);
		assertThat(emparejar.consultar(vinculado)).isEqualTo(EstadoEmparejamiento.VINCULADO);
		assertThat(emparejar.consultar("otro")).isEqualTo(EstadoEmparejamiento.VENCIDO);
		assertThat(emparejar.consultar(" ")).isEqualTo(EstadoEmparejamiento.VENCIDO);

		ahora = AHORA.plus(VIGENCIA);
		assertThat(emparejar.consultar(pendiente)).isEqualTo(EstadoEmparejamiento.VENCIDO);
	}

	/** Da lo mismo cómo se escriba el código: espacios, guiones y minúsculas. */
	@Test
	void normalizaElCodigoEscrito() {
		assertThat(CodigoEmparejamiento.normalizar(" k7pm-42qx ")).isEqualTo("K7PM42QX");
		assertThat(CodigoEmparejamiento.normalizar("K7PM 42QX")).isEqualTo("K7PM42QX");
		assertThat(CodigoEmparejamiento.normalizar(null)).isEmpty();
	}

}
