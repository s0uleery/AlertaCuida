package cl.ucn.alertacuida.application.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.sesion.error.InicioSesionFallido;
import cl.ucn.alertacuida.application.sesion.modelo.CuentaUsuario;
import cl.ucn.alertacuida.application.sesion.modelo.SesionIniciada;
import cl.ucn.alertacuida.application.sesion.modelo.TokenAcceso;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import cl.ucn.alertacuida.application.sesion.puerto.RegistroAccesos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Inicio de sesión, sin base de datos. */
class IniciarSesionTest {

	private static final Instant AHORA = Instant.parse("2026-10-21T12:00:00Z");
	private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private final CuentasEnMemoria cuentas = new CuentasEnMemoria();
	private final AccesosEnMemoria accesos = new AccesosEnMemoria();
	private IniciarSesion iniciarSesion;

	/** Un administrador activo con contraseña "clave" y bloqueo tras 3 fallos. */
	@BeforeEach
	void preparar() {
		// El "hash" del RUN es el RUN normalizado y el de la contraseña es "hash:" + contraseña
		iniciarSesion = new IniciarSesion(cuentas, accesos, run -> "h-" + run.valor(),
				(contrasena, hash) -> hash != null && hash.equals("hash:" + contrasena),
				usuario -> new TokenAcceso("token-" + usuario.id(), AHORA.plusSeconds(900)),
				new PoliticaBloqueo(3, Duration.ofMinutes(15)), Clock.fixed(AHORA, ZoneOffset.UTC));
		cuentas.agregar("h-11111111-1", new CuentaUsuario(ADMIN, "Admin", "ADMINISTRADOR", "hash:clave", true));
		cuentas.permisos.put("ADMINISTRADOR", Set.of("ALERTAS:VER", "USUARIOS:CREAR"));
	}

	/** Con RUN y contraseña correctos, entra con los permisos de su rol y queda en la auditoría. */
	@Test
	void conRunYContrasenaCorrectosEntraConLosPermisosDeSuRol() {
		SesionIniciada sesion = iniciarSesion.iniciar("11.111.111-1", "clave", "127.0.0.1");

		assertThat(sesion.token().valor()).isEqualTo("token-" + ADMIN);
		assertThat(sesion.usuario().codigoRol()).isEqualTo("ADMINISTRADOR");
		assertThat(sesion.usuario().permisos()).containsExactlyInAnyOrder("ALERTAS:VER", "USUARIOS:CREAR");
		assertThat(accesos.registros).containsExactly("LOGIN " + ADMIN);
	}

	/** Con contraseña incorrecta falla, y el intento queda en la auditoría. */
	@Test
	void contrasenaIncorrectaFallaYQuedaEnLaAuditoria() {
		assertThatThrownBy(() -> iniciarSesion.iniciar("11111111-1", "otra", "ip"))
				.isInstanceOf(InicioSesionFallido.class);
		assertThat(accesos.registros).containsExactly("LOGIN_FALLIDO " + ADMIN);
	}

	/** Un RUN inexistente, uno inválido y una contraseña incorrecta dan el mismo mensaje. */
	@Test
	void runInexistenteOInvalidoFallaConElMismoMensaje() {
		String inexistente = mensaje(() -> iniciarSesion.iniciar("12.345.678-5", "clave", "ip"));
		String invalido = mensaje(() -> iniciarSesion.iniciar("no-es-run", "clave", "ip"));
		String contrasenaMala = mensaje(() -> iniciarSesion.iniciar("11111111-1", "otra", "ip"));

		assertThat(inexistente).isEqualTo(invalido).isEqualTo(contrasenaMala);
		assertThat(accesos.registros).startsWith("LOGIN_FALLIDO null", "LOGIN_FALLIDO null");
	}

	/** Una cuenta que no se ha activado no entra. */
	@Test
	void cuentaSinActivarNoEntra() {
		UUID nueva = UUID.randomUUID();
		cuentas.agregar("h-12345678-5", new CuentaUsuario(nueva, "Nueva", "PERSONAL", null, false));

		assertThatThrownBy(() -> iniciarSesion.iniciar("12345678-5", "clave", "ip"))
				.isInstanceOf(InicioSesionFallido.class);
	}

	/** Una cuenta desactivada no entra aunque la contraseña sea correcta. */
	@Test
	void cuentaDesactivadaNoEntraAunqueLaContrasenaSeaCorrecta() {
		UUID desactivada = UUID.randomUUID();
		cuentas.agregar("h-12345678-5", new CuentaUsuario(desactivada, "Ex", "PERSONAL", "hash:clave", false));

		assertThatThrownBy(() -> iniciarSesion.iniciar("12345678-5", "clave", "ip"))
				.isInstanceOf(InicioSesionFallido.class);
	}

	/** Tras tres fallos la cuenta se bloquea, aunque después la contraseña sea correcta. */
	@Test
	void trasTresFallosSeBloqueaAunqueLaContrasenaSeaCorrecta() {
		for (int i = 0; i < 3; i++) {
			mensaje(() -> iniciarSesion.iniciar("11111111-1", "otra", "ip"));
		}
		assertThatThrownBy(() -> iniciarSesion.iniciar("11111111-1", "clave", "ip"))
				.isInstanceOf(InicioSesionFallido.class);
	}

	/** Dos fallos todavía no bloquean. */
	@Test
	void dosFallosNoBloquean() {
		mensaje(() -> iniciarSesion.iniciar("11111111-1", "otra", "ip"));
		mensaje(() -> iniciarSesion.iniciar("11111111-1", "otra", "ip"));

		assertThat(iniciarSesion.iniciar("11111111-1", "clave", "ip").usuario().id()).isEqualTo(ADMIN);
	}

	/** @return el mensaje del error de inicio de sesión que lanza {@code intento} */
	private static String mensaje(Runnable intento) {
		try {
			intento.run();
		} catch (InicioSesionFallido fallo) {
			return fallo.getMessage();
		}
		throw new AssertionError("Se esperaba que el inicio de sesión fallara");
	}

	/** Doble de {@link CuentaUsuarioRepository}: cuentas por hash del RUN y permisos por rol. */
	private static final class CuentasEnMemoria implements CuentaUsuarioRepository {

		private final Map<String, CuentaUsuario> porRunHash = new HashMap<>();
		private final Map<String, Set<String>> permisos = new HashMap<>();

		/** Guarda una cuenta con ese hash de RUN. */
		void agregar(String runHash, CuentaUsuario cuenta) {
			porRunHash.put(runHash, cuenta);
		}

		@Override
		public Optional<CuentaUsuario> buscarPorRunHash(String runHash) {
			return Optional.ofNullable(porRunHash.get(runHash));
		}

		@Override
		public Set<String> permisosDelRol(String codigoRol) {
			return permisos.getOrDefault(codigoRol, Set.of());
		}

	}

	/** Sin LOGIN posterior, todos los fallos registrados cuentan (el reloj está detenido). */
	private static final class AccesosEnMemoria implements RegistroAccesos {

		private final List<String> registros = new ArrayList<>();

		@Override
		public void registrarLogin(UUID idUsuario, String ip, Instant fechaHora) {
			registros.add("LOGIN " + idUsuario);
		}

		@Override
		public void registrarLoginFallido(UUID idUsuario, String ip, Instant fechaHora) {
			registros.add("LOGIN_FALLIDO " + idUsuario);
		}

		@Override
		public long contarFallidosRecientes(UUID idUsuario, Instant desde) {
			int ultimoLogin = registros.lastIndexOf("LOGIN " + idUsuario);
			return registros.subList(ultimoLogin + 1, registros.size()).stream()
					.filter(("LOGIN_FALLIDO " + idUsuario)::equals).count();
		}

	}

}
