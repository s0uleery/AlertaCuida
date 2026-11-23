package cl.ucn.alertacuida.application.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.sesion.error.SesionNoRenovable;
import cl.ucn.alertacuida.application.sesion.modelo.CuentaUsuario;
import cl.ucn.alertacuida.application.sesion.modelo.SesionIniciada;
import cl.ucn.alertacuida.application.sesion.modelo.TokenAcceso;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import cl.ucn.alertacuida.application.soporte.TokensSesionEnMemoria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Renovación y cierre de sesión, sin base de datos: rotación, inactividad, límite y reutilización. */
class RenovarSesionTest {

	private static final Instant INICIO = Instant.parse("2026-11-03T12:00:00Z");
	private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String IP = "203.0.113.7";
	private static final PoliticaSesion POLITICA = new PoliticaSesion(Duration.ofHours(10), Duration.ofMinutes(15),
			Duration.ofSeconds(10));

	private final RelojMovible reloj = new RelojMovible();
	private final TokensSesionEnMemoria tokens = new TokensSesionEnMemoria();
	private final CuentasDePrueba cuentas = new CuentasDePrueba();
	private final SecretosDePrueba secretos = new SecretosDePrueba();
	private final List<String> auditados = new ArrayList<>();
	private AperturaSesion apertura;
	private RenovarSesion renovar;
	private CerrarSesion cerrar;

	/** Una cuenta activa, con tokens en memoria y un reloj que las pruebas pueden adelantar. */
	@BeforeEach
	void preparar() {
		apertura = new AperturaSesion(cuentas, tokens, secretos,
				usuario -> new TokenAcceso("acceso-" + usuario.id(), INICIO.plusSeconds(900)), POLITICA);
		Transacciones sinTransaccion = new Transacciones() {
			@Override
			public <T> T ejecutar(java.util.function.Supplier<T> trabajo) {
				return trabajo.get();
			}
		};
		renovar = new RenovarSesion(tokens, cuentas, secretos, apertura, POLITICA,
				(id, accion, entidad, idEntidad, ip, fecha, detalle) -> auditados.add(accion + " " + id + " " + ip),
				sinTransaccion, reloj);
		cerrar = new CerrarSesion(tokens, secretos);
	}

	/** Abre una sesión de Ana a la hora actual del reloj y devuelve su token de renovación. */
	private String abrir() {
		return apertura.abrir(cuentas.ana, reloj.instant()).renovacion().valor();
	}

	/** Renovar entrega tokens nuevos, con el usuario y sus permisos. */
	@Test
	void renovarEntregaTokensNuevos() {
		String inicial = abrir();

		SesionIniciada sesion = renovar.renovar(inicial, IP);

		assertThat(sesion.token().valor()).isEqualTo("acceso-" + ANA);
		assertThat(sesion.usuario().permisos()).containsExactly("ALERTAS:VER");
		assertThat(sesion.renovacion().valor()).isNotEqualTo(inicial);
	}

	/** Un cambio de permisos se nota en la siguiente renovación. */
	@Test
	void renovarLeeLosPermisosActuales() {
		String inicial = abrir();
		cuentas.permisos = Set.of("ALERTAS:VER", "REPORTES:VER");

		assertThat(renovar.renovar(inicial, IP).usuario().permisos()).contains("REPORTES:VER");
	}

	/** Renovar no alarga el límite: se mantiene a 10 horas del login. */
	@Test
	void renovarNoMueveElLimiteDeLaSesion() {
		String token = abrir();
		reloj.adelantar(Duration.ofMinutes(14));

		SesionIniciada sesion = renovar.renovar(token, IP);

		assertThat(sesion.renovacion().expiraEn()).isEqualTo(INICIO.plus(Duration.ofHours(10)));
	}

	/** Usando el dashboard de forma continua, la sesión termina a las 10 horas. */
	@Test
	void lasesionTerminaAlLimiteAunqueSigaEnUso() {
		String token = abrir();
		for (int i = 0; i < 42; i++) {
			reloj.adelantar(Duration.ofMinutes(14));
			token = renovar.renovar(token, IP).renovacion().valor();
		}
		// 42 x 14 min = 9 h 48 min. Con otros 14 min se pasa de las 10 horas
		reloj.adelantar(Duration.ofMinutes(14));
		String ultimo = token;

		assertThatThrownBy(() -> renovar.renovar(ultimo, IP)).isInstanceOf(SesionNoRenovable.class);
	}

	/** Sin renovar durante el plazo de inactividad, la sesión termina. */
	@Test
	void lainactividadTerminaLaSesion() {
		String token = abrir();
		reloj.adelantar(Duration.ofMinutes(15));

		assertThatThrownBy(() -> renovar.renovar(token, IP)).isInstanceOf(SesionNoRenovable.class);
	}

	/** Justo antes del plazo de inactividad todavía se puede renovar. */
	@Test
	void antesDelPlazoDeInactividadSePuedeRenovar() {
		String token = abrir();
		reloj.adelantar(Duration.ofMinutes(15).minusSeconds(1));

		assertThat(renovar.renovar(token, IP).usuario().id()).isEqualTo(ANA);
	}

	/** Dos pestañas pueden usar el mismo token a la vez, dentro de la gracia. */
	@Test
	void dentroDeLaGraciaUnTokenReemplazadoSirve() {
		String token = abrir();
		renovar.renovar(token, IP);
		reloj.adelantar(Duration.ofSeconds(9));

		assertThat(renovar.renovar(token, IP).usuario().id()).isEqualTo(ANA);
	}

	/** Reutilizar un token pasada la gracia termina todas las sesiones de la cuenta. */
	@Test
	void reutilizarUnTokenTerminaTodasLasSesiones() {
		String robado = abrir();
		String vigente = renovar.renovar(robado, IP).renovacion().valor();
		String otroComputador = abrir();
		reloj.adelantar(Duration.ofSeconds(10));

		assertThatThrownBy(() -> renovar.renovar(robado, IP)).isInstanceOf(SesionNoRenovable.class);

		assertThat(tokens.cantidad()).isZero();
		assertThatThrownBy(() -> renovar.renovar(vigente, IP)).isInstanceOf(SesionNoRenovable.class);
		assertThatThrownBy(() -> renovar.renovar(otroComputador, IP)).isInstanceOf(SesionNoRenovable.class);
	}

	/** La reutilización queda en la auditoría, con la cuenta afectada y desde dónde llegó. */
	@Test
	void reutilizarUnTokenQuedaEnLaAuditoria() {
		String robado = abrir();
		renovar.renovar(robado, IP);
		reloj.adelantar(Duration.ofSeconds(10));

		assertThatThrownBy(() -> renovar.renovar(robado, IP)).isInstanceOf(SesionNoRenovable.class);

		assertThat(auditados).containsExactly("TOKEN_REUTILIZADO " + ANA + " " + IP);
	}

	/** Dos pestañas dentro de la gracia no son un robo: no queda nada en la auditoría. */
	@Test
	void dentroDeLaGraciaNoSeRegistraReutilizacion() {
		String token = abrir();
		renovar.renovar(token, IP);

		renovar.renovar(token, IP);

		assertThat(auditados).isEmpty();
	}

	/** Una cuenta desactivada no renueva, y sus sesiones terminan. */
	@Test
	void cuentaDesactivadaNoRenueva() {
		String token = abrir();
		cuentas.ana = new CuentaUsuario(ANA, "Ana", "PERSONAL", "hash", false);

		assertThatThrownBy(() -> renovar.renovar(token, IP)).isInstanceOf(SesionNoRenovable.class);
		assertThat(tokens.cantidad()).isZero();
	}

	/** Sin cookie, o con un token inventado, no se renueva. */
	@Test
	void sinTokenOConTokenFalsoNoSeRenueva() {
		assertThatThrownBy(() -> renovar.renovar(null, IP)).isInstanceOf(SesionNoRenovable.class);
		assertThatThrownBy(() -> renovar.renovar(" ", IP)).isInstanceOf(SesionNoRenovable.class);
		assertThatThrownBy(() -> renovar.renovar("inventado", IP)).isInstanceOf(SesionNoRenovable.class);
	}

	/** Tras cerrar sesión el token no sirve, ni siquiera dentro de la gracia. */
	@Test
	void trasCerrarSesionElTokenNoSirve() {
		String token = abrir();

		cerrar.cerrar(token);

		assertThatThrownBy(() -> renovar.renovar(token, IP)).isInstanceOf(SesionNoRenovable.class);
	}

	/** Cerrar sesión en un computador no cierra las de otros. */
	@Test
	void cerrarSesionNoAfectaAOtrosComputadores() {
		String este = abrir();
		String otro = abrir();

		cerrar.cerrar(este);

		assertThat(renovar.renovar(otro, IP).usuario().id()).isEqualTo(ANA);
	}

	/** Cerrar sesión sin cookie no falla. */
	@Test
	void cerrarSesionSinTokenNoFalla() {
		cerrar.cerrar(null);
		cerrar.cerrar("");

		assertThat(tokens.cantidad()).isZero();
	}

	/** La limpieza borra los vencidos de cualquier cuenta y conserva los vigentes. */
	@Test
	void laLimpiezaBorraLosVencidosDeTodasLasCuentas() {
		abrir();
		tokens.guardar(UUID.randomUUID(), "hash:de-otra-cuenta", INICIO, INICIO.plus(Duration.ofHours(1)));
		tokens.guardar(UUID.randomUUID(), "hash:vigente", INICIO, INICIO.plus(Duration.ofHours(20)));
		reloj.adelantar(Duration.ofHours(10));

		new LimpiarTokensVencidos(tokens, reloj).limpiar();

		assertThat(tokens.cantidad()).isEqualTo(1);
		assertThat(tokens.buscar("hash:vigente")).isPresent();
	}

	/** Reloj que las pruebas pueden adelantar. */
	private static final class RelojMovible extends Clock {

		private Instant ahora = INICIO;

		/** Avanza la hora. */
		void adelantar(Duration tiempo) {
			ahora = ahora.plus(tiempo);
		}

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

	}

	/** Una sola cuenta, cuyos datos y permisos las pruebas pueden cambiar. */
	private static final class CuentasDePrueba implements CuentaUsuarioRepository {

		private CuentaUsuario ana = new CuentaUsuario(ANA, "Ana", "PERSONAL", "hash", true);
		private Set<String> permisos = Set.of("ALERTAS:VER");

		@Override
		public Optional<CuentaUsuario> buscarPorRunHash(String runHash) {
			return Optional.empty();
		}

		@Override
		public Optional<CuentaUsuario> buscarPorId(UUID id) {
			return ana.id().equals(id) ? Optional.of(ana) : Optional.empty();
		}

		@Override
		public Set<String> permisosDelRol(String codigoRol) {
			return new HashSet<>(permisos);
		}

	}

	/** Secretos numerados, para que cada uno sea distinto y fácil de seguir. */
	private static final class SecretosDePrueba implements SecretosUnSoloUso {

		private int siguiente;

		@Override
		public String nuevo() {
			siguiente++;
			return "secreto-" + siguiente;
		}

		@Override
		public String hash(String secreto) {
			return "hash:" + secreto;
		}

	}

}
