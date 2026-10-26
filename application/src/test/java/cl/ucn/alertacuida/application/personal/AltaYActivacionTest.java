package cl.ucn.alertacuida.application.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.error.CuentaDuplicada;
import cl.ucn.alertacuida.application.personal.error.CuentaNoPendiente;
import cl.ucn.alertacuida.application.personal.error.EnlaceInvalido;
import cl.ucn.alertacuida.application.personal.modelo.CuentaPendiente;
import cl.ucn.alertacuida.application.personal.modelo.DatosCuenta;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import cl.ucn.alertacuida.application.personal.modelo.NuevaCuenta;
import cl.ucn.alertacuida.application.personal.modelo.TokenActivacion;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import cl.ucn.alertacuida.application.personal.puerto.TokensActivacion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Alta de cuentas del personal y su activación con enlace, sin base de datos. */
class AltaYActivacionTest {

	private static final Instant AHORA = Instant.parse("2026-10-22T12:00:00Z");
	private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String CLAVE = "una frase larga y propia";

	private final RelojMovible reloj = new RelojMovible(AHORA);
	private final CuentasEnMemoria cuentas = new CuentasEnMemoria();
	private final TokensEnMemoria tokens = new TokensEnMemoria();
	private final List<String> auditoria = new ArrayList<>();
	private CrearCuentaPersonal crear;
	private NuevoEnlaceActivacion nuevoEnlace;
	private ActivarCuenta activar;

	/** Casos de uso con dobles en memoria y un reloj que las pruebas pueden adelantar. */
	@BeforeEach
	void preparar() {
		// El "hash" del RUN es "h-" + RUN normalizado; el de la contraseña, "hash:" + contraseña
		SecretosUnSoloUso secretos = new SecretosContados();
		EmisorEnlacesActivacion enlaces = new EmisorEnlacesActivacion(tokens, secretos, Duration.ofHours(48));
		RegistroAuditoria registro = (usuario, accion, entidad, id, ip, fecha) ->
				auditoria.add(accion + " " + entidad + " por " + usuario);
		// Un método genérico no admite lambda, pero sí referencia a método
		Transacciones enTransaccion = Supplier::get;
		crear = new CrearCuentaPersonal(cuentas, run -> "h-" + run.valor(), enlaces, registro, enTransaccion, reloj);
		nuevoEnlace = new NuevoEnlaceActivacion(cuentas, enlaces, registro, enTransaccion, reloj);
		activar = new ActivarCuenta(tokens, secretos, cuentas, run -> "h-" + run.valor(),
				contrasena -> "hash:" + contrasena, registro, enTransaccion, reloj);
	}

	/** La cuenta nace inactiva, sin contraseña y con un enlace que vence en 48 horas. */
	@Test
	void laCuentaNaceInactivaSinContrasenaYConUnEnlaceDe48Horas() {
		EnlaceActivacion enlace = crear.crear(datos("12.345.678-5", "Ana@Muni.cl"), ADMIN, "ip");

		CuentaGuardada guardada = cuentas.guardadas.get(enlace.idUsuario());
		assertThat(guardada.cuenta().run()).isEqualTo("12345678-5");
		assertThat(guardada.cuenta().correo()).isEqualTo("ana@muni.cl");
		assertThat(guardada.hashContrasena()).isNull();
		assertThat(enlace.expiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(48)));
		assertThat(tokens.porHash).containsOnlyKeys("hash-" + enlace.token());
		assertThat(auditoria).containsExactly("CREAR USUARIO por " + ADMIN);
	}

	/** Se rechazan el RUN inválido, el rol inexistente y el RUN o correo repetidos. */
	@Test
	void runInvalidoRolInexistenteYDuplicadosSeRechazan() {
		crear.crear(datos("12.345.678-5", "ana@muni.cl"), ADMIN, "ip");

		assertThatThrownBy(() -> crear.crear(datos("12.345.678-0", "otra@muni.cl"), ADMIN, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> crear.crear(new DatosCuenta("11.111.111-1", "Luis", null, "luis@muni.cl", "JEFE"),
				ADMIN, "ip")).isInstanceOf(SolicitudRechazada.class).hasMessageContaining("rol");
		assertThatThrownBy(() -> crear.crear(datos("12345678-5", "otra@muni.cl"), ADMIN, "ip"))
				.isInstanceOf(CuentaDuplicada.class).hasMessageContaining("RUN");
		assertThatThrownBy(() -> crear.crear(datos("11.111.111-1", "ANA@muni.cl"), ADMIN, "ip"))
				.isInstanceOf(CuentaDuplicada.class).hasMessageContaining("correo");
	}

	/** Con el enlace, la persona elige su contraseña y la cuenta queda activa. */
	@Test
	void conElEnlaceEligeSuContrasenaYLaCuentaQuedaActiva() {
		EnlaceActivacion enlace = crear.crear(datos("12.345.678-5", "ana@muni.cl"), ADMIN, "ip");

		activar.activar(enlace.token(), CLAVE, "ip");

		assertThat(cuentas.guardadas.get(enlace.idUsuario()).hashContrasena()).isEqualTo("hash:" + CLAVE);
		assertThat(auditoria).last().isEqualTo("MODIFICAR USUARIO por " + enlace.idUsuario());
	}

	/** El enlace sirve una sola vez. */
	@Test
	void elEnlaceSirveUnaSolaVez() {
		EnlaceActivacion enlace = crear.crear(datos("12.345.678-5", "ana@muni.cl"), ADMIN, "ip");
		activar.activar(enlace.token(), CLAVE, "ip");

		assertThatThrownBy(() -> activar.activar(enlace.token(), "otra frase larga mas", "ip"))
				.isInstanceOf(EnlaceInvalido.class);
	}

	/** Un enlace vencido o inventado no sirve. */
	@Test
	void elEnlaceVencidoOInventadoNoSirve() {
		EnlaceActivacion enlace = crear.crear(datos("12.345.678-5", "ana@muni.cl"), ADMIN, "ip");
		reloj.avanzar(Duration.ofHours(48));

		assertThatThrownBy(() -> activar.activar(enlace.token(), CLAVE, "ip")).isInstanceOf(EnlaceInvalido.class);
		assertThatThrownBy(() -> activar.activar("inventado", CLAVE, "ip")).isInstanceOf(EnlaceInvalido.class);
	}

	/** Una contraseña débil se rechaza, pero el enlace sigue sirviendo para intentar de nuevo. */
	@Test
	void unaContrasenaDebilSeRechazaPeroElEnlaceSigueSirviendo() {
		EnlaceActivacion enlace = crear.crear(datos("12.345.678-5", "ana@muni.cl"), ADMIN, "ip");

		assertThatThrownBy(() -> activar.activar(enlace.token(), "corta", "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> activar.activar(enlace.token(), "12.345.678-5", "ip"))
				.isInstanceOf(SolicitudRechazada.class).hasMessageContaining("RUN");
		activar.activar(enlace.token(), CLAVE, "ip");
	}

	/** Un enlace nuevo anula al anterior. */
	@Test
	void unEnlaceNuevoAnulaElAnterior() {
		EnlaceActivacion primero = crear.crear(datos("12.345.678-5", "ana@muni.cl"), ADMIN, "ip");
		EnlaceActivacion segundo = nuevoEnlace.emitir(primero.idUsuario(), ADMIN, "ip");

		assertThatThrownBy(() -> activar.activar(primero.token(), CLAVE, "ip")).isInstanceOf(EnlaceInvalido.class);
		activar.activar(segundo.token(), CLAVE, "ip");
	}

	/** Una cuenta ya activa, o que no existe, no recibe enlaces. */
	@Test
	void unaCuentaYaActivaNoRecibeEnlaces() {
		EnlaceActivacion enlace = crear.crear(datos("12.345.678-5", "ana@muni.cl"), ADMIN, "ip");
		activar.activar(enlace.token(), CLAVE, "ip");

		assertThatThrownBy(() -> nuevoEnlace.emitir(enlace.idUsuario(), ADMIN, "ip"))
				.isInstanceOf(CuentaNoPendiente.class);
		assertThatThrownBy(() -> nuevoEnlace.emitir(UUID.randomUUID(), ADMIN, "ip"))
				.isInstanceOf(CuentaNoPendiente.class);
	}

	/** @return datos de una persona con rol PERSONAL, con ese RUN y correo */
	private static DatosCuenta datos(String run, String correo) {
		return new DatosCuenta(run, " Ana Pérez ", "Enfermera", correo, "PERSONAL");
	}

	/** Una cuenta guardada en el doble en memoria, con su hash de contraseña (null = pendiente). */
	private record CuentaGuardada(NuevaCuenta cuenta, String hashContrasena) {
	}

	/** Doble de {@link CuentasPersonal}: guarda en un mapa y conoce los tres roles iniciales. */
	private static final class CuentasEnMemoria implements CuentasPersonal {

		private final Map<UUID, CuentaGuardada> guardadas = new HashMap<>();

		@Override
		public boolean existeRunHash(String runHash) {
			return guardadas.values().stream().anyMatch(g -> g.cuenta().runHash().equals(runHash));
		}

		@Override
		public boolean existeCorreo(String correo) {
			return guardadas.values().stream().anyMatch(g -> g.cuenta().correo().equals(correo));
		}

		@Override
		public boolean rolActivo(String codigoRol) {
			return List.of("ADMINISTRADOR", "COORDINADOR", "PERSONAL").contains(codigoRol);
		}

		@Override
		public UUID crear(NuevaCuenta cuenta) {
			UUID id = UUID.randomUUID();
			guardadas.put(id, new CuentaGuardada(cuenta, null));
			return id;
		}

		@Override
		public Optional<CuentaPendiente> buscarPendiente(UUID id) {
			return Optional.ofNullable(guardadas.get(id))
					.filter(g -> g.hashContrasena() == null)
					.map(g -> new CuentaPendiente(id, g.cuenta().runHash()));
		}

		@Override
		public boolean activar(UUID id, String hashContrasena) {
			if (buscarPendiente(id).isEmpty()) {
				return false;
			}
			guardadas.put(id, new CuentaGuardada(guardadas.get(id).cuenta(), hashContrasena));
			return true;
		}

	}

	/** Doble de {@link TokensActivacion}: guarda los enlaces sin usar por su hash. */
	private static final class TokensEnMemoria implements TokensActivacion {

		private final Map<String, TokenActivacion> porHash = new HashMap<>();

		@Override
		public void guardar(UUID idUsuario, String hashToken, Instant emision, Instant expiracion) {
			porHash.put(hashToken, new TokenActivacion(UUID.randomUUID(), idUsuario, expiracion));
		}

		@Override
		public void anularPendientes(UUID idUsuario) {
			porHash.values().removeIf(token -> token.idUsuario().equals(idUsuario));
		}

		@Override
		public Optional<TokenActivacion> buscarSinUsar(String hashToken) {
			return Optional.ofNullable(porHash.get(hashToken));
		}

		@Override
		public boolean marcarUsado(UUID idToken, Instant fechaUso) {
			return porHash.values().removeIf(token -> token.id().equals(idToken));
		}

	}

	/** Doble de {@link SecretosUnSoloUso}: entrega "secreto-1", "secreto-2"… y su hash es "hash-" + secreto. */
	private static final class SecretosContados implements SecretosUnSoloUso {

		private int emitidos;

		@Override
		public String nuevo() {
			return "secreto-" + ++emitidos;
		}

		@Override
		public String hash(String secreto) {
			return "hash-" + secreto;
		}

	}

	/** Reloj que las pruebas pueden adelantar, para probar enlaces vencidos. */
	private static final class RelojMovible extends Clock {

		private Instant ahora;

		/** @param ahora hora inicial */
		RelojMovible(Instant ahora) {
			this.ahora = ahora;
		}

		/** Adelanta el reloj en {@code tiempo}. */
		void avanzar(Duration tiempo) {
			ahora = ahora.plus(tiempo);
		}

		@Override
		public Instant instant() {
			return ahora;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zona) {
			return this;
		}

	}

}
