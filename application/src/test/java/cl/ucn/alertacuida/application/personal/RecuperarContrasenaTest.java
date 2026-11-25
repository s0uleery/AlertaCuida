package cl.ucn.alertacuida.application.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.error.EnlaceInvalido;
import cl.ucn.alertacuida.application.personal.modelo.CuentaRecuperable;
import cl.ucn.alertacuida.application.personal.modelo.TokenActivacion;
import cl.ucn.alertacuida.application.personal.puerto.CuentasRecuperacion;
import cl.ucn.alertacuida.application.personal.puerto.TokensRecuperacion;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.application.soporte.TextosCorreoDePrueba;
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

/**
 * Recuperar la contraseña sin base de datos, según las decisiones del 24-11-2026: enlace por correo con el RUN,
 * solo para cuentas activas, de un solo uso, que vence en 1 hora, con un tope de 3 por hora y la misma política
 * de contraseña que la activación.
 */
class RecuperarContrasenaTest {

	private static final Instant AHORA = Instant.parse("2026-11-24T12:00:00Z");
	private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final String URL = "https://alertacuida.app/restablecer";
	private static final String CLAVE = "una frase larga y propia";

	private final RelojMovible reloj = new RelojMovible(AHORA);
	private final Map<UUID, CuentaRecuperable> activas = new HashMap<>();
	private final Map<UUID, String> contrasenas = new HashMap<>();
	private final TokensEnMemoria tokens = new TokensEnMemoria();
	private final List<Mensaje> enviados = new ArrayList<>();
	private final List<String> auditoria = new ArrayList<>();
	private SolicitarRecuperacion solicitar;
	private RestablecerContrasena restablecer;

	/** Casos de uso con dobles en memoria. El hash del RUN es "h-" + RUN; el de la contraseña, "hash:" + ella. */
	@BeforeEach
	void preparar() {
		activas.put(ANA, new CuentaRecuperable(ANA, "h-12345678-5", "Ana Pérez", "ana@muni.cl"));
		SecretosUnSoloUso secretos = new SecretosContados();
		Transacciones enTransaccion = Supplier::get;
		CuentasRecuperacion cuentas = new CuentasEnMemoria();
		CanalNotificacion canal = mensaje -> {
			enviados.add(mensaje);
			return "id-" + enviados.size();
		};
		solicitar = new SolicitarRecuperacion(cuentas, tokens, secretos, run -> "h-" + run.valor(),
				new PlantillasCorreo("https://alertacuida.app", TextosCorreoDePrueba.textos(), sinFirma(),
						Duration.ofMinutes(15)),
				canal, enTransaccion, reloj, URL, Duration.ofHours(1), 3);
		restablecer = new RestablecerContrasena(tokens, secretos, cuentas, run -> "h-" + run.valor(),
				contrasena -> "hash:" + contrasena,
				(usuario, accion, entidad, id, ip, fecha, detalle) -> auditoria.add(accion + " " + entidad + " " + id),
				enTransaccion, reloj);
	}

	/** Con una cuenta activa, el correo llega a ella con el enlace, que vence en una hora. */
	@Test
	void laCuentaActivaRecibeElEnlace() {
		solicitar.solicitar("12.345.678-5");

		assertThat(enviados).hasSize(1);
		assertThat(enviados.get(0).destinatario()).isEqualTo("ana@muni.cl");
		assertThat(enviados.get(0).texto()).contains("Ana Pérez").contains(URL + "#secreto-1");
		assertThat(tokens.guardados).singleElement()
				.satisfies(token -> assertThat(token.expiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(1))));
	}

	/** Sin cuenta activa o sin correo no se envía nada, y tampoco se avisa: la respuesta es la misma. */
	@Test
	void sinCuentaActivaOSinCorreoNoSeEnviaNada() {
		solicitar.solicitar("11.111.111-1");
		activas.put(ANA, new CuentaRecuperable(ANA, "h-12345678-5", "Ana Pérez", " "));
		solicitar.solicitar("12.345.678-5");

		assertThat(enviados).isEmpty();
		assertThat(tokens.guardados).isEmpty();
	}

	/** Un RUN mal escrito sí es un error: no revela si la cuenta existe. */
	@Test
	void unRunMalEscritoSeRechaza() {
		assertThatThrownBy(() -> solicitar.solicitar("12.345.678-0")).isInstanceOf(SolicitudRechazada.class);
	}

	/** Como máximo 3 en una hora; pasada la hora, se puede pedir otro. */
	@Test
	void comoMaximoTresPorHora() {
		for (int i = 0; i < 4; i++) {
			solicitar.solicitar("12.345.678-5");
		}
		assertThat(enviados).hasSize(3);

		reloj.adelantar(Duration.ofMinutes(61));
		solicitar.solicitar("12.345.678-5");

		assertThat(enviados).hasSize(4);
	}

	/** Con el enlace se cambia la contraseña, una sola vez, y queda auditado. */
	@Test
	void elEnlaceCambiaLaContrasenaUnaSolaVez() {
		solicitar.solicitar("12.345.678-5");

		restablecer.restablecer("secreto-1", CLAVE, "ip");

		assertThat(contrasenas).containsEntry(ANA, "hash:" + CLAVE);
		assertThat(auditoria).containsExactly("MODIFICAR USUARIO " + ANA);
		assertThatThrownBy(() -> restablecer.restablecer("secreto-1", CLAVE, "ip"))
				.isInstanceOf(EnlaceInvalido.class);
	}

	/** Solo sirve el último enlace emitido. */
	@Test
	void soloSirveElUltimo() {
		solicitar.solicitar("12.345.678-5");
		solicitar.solicitar("12.345.678-5");

		assertThatThrownBy(() -> restablecer.restablecer("secreto-1", CLAVE, "ip"))
				.isInstanceOf(EnlaceInvalido.class);
		restablecer.restablecer("secreto-2", CLAVE, "ip");
	}

	/** A la hora justa el enlace ya no sirve. */
	@Test
	void venceALaHora() {
		solicitar.solicitar("12.345.678-5");
		reloj.adelantar(Duration.ofHours(1));

		assertThatThrownBy(() -> restablecer.restablecer("secreto-1", CLAVE, "ip"))
				.isInstanceOf(EnlaceInvalido.class);
	}

	/** Una cuenta desactivada después de pedir el enlace ya no puede usarlo. */
	@Test
	void unaCuentaDesactivadaNoRestablece() {
		solicitar.solicitar("12.345.678-5");
		activas.remove(ANA);

		assertThatThrownBy(() -> restablecer.restablecer("secreto-1", CLAVE, "ip"))
				.isInstanceOf(EnlaceInvalido.class);
		assertThat(contrasenas).isEmpty();
	}

	/** Una contraseña débil o igual al RUN se rechaza, y el enlace sigue sirviendo. */
	@Test
	void laPoliticaDeContrasenaSeAplica() {
		solicitar.solicitar("12.345.678-5");

		assertThatThrownBy(() -> restablecer.restablecer("secreto-1", "corta", "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> restablecer.restablecer("secreto-1", "12.345.678-5", "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		restablecer.restablecer("secreto-1", CLAVE, "ip");
		assertThat(contrasenas).containsKey(ANA);
	}

	/** @return una firma que no se usa: estos correos no llevan enlace de respuesta */
	private static FirmaRespuesta sinFirma() {
		return new FirmaRespuesta() {
			@Override
			public String firmar(UUID idIntento) {
				return "firma";
			}

			@Override
			public Optional<UUID> verificar(String token) {
				return Optional.empty();
			}
		};
	}

	/** Cuentas activas de la prueba. */
	private final class CuentasEnMemoria implements CuentasRecuperacion {

		@Override
		public Optional<CuentaRecuperable> buscarActivaPorRunHash(String runHash) {
			return activas.values().stream().filter(cuenta -> cuenta.runHash().equals(runHash)).findFirst();
		}

		@Override
		public Optional<CuentaRecuperable> buscarActiva(UUID id) {
			return Optional.ofNullable(activas.get(id));
		}

		@Override
		public boolean cambiarContrasena(UUID id, String hashContrasena) {
			if (!activas.containsKey(id)) {
				return false;
			}
			contrasenas.put(id, hashContrasena);
			return true;
		}

	}

	/** Enlaces en memoria; el último emitido de cada cuenta es el único que sirve. */
	private static final class TokensEnMemoria implements TokensRecuperacion {

		private final List<Guardado> guardados = new ArrayList<>();

		/** Un enlace guardado. */
		private record Guardado(UUID id, UUID idUsuario, String hash, Instant emision, Instant expiraEn,
				boolean usado) {
		}

		@Override
		public void guardar(UUID idUsuario, String hashToken, Instant emision, Instant expiracion) {
			guardados.add(new Guardado(UUID.randomUUID(), idUsuario, hashToken, emision, expiracion, false));
		}

		@Override
		public int emitidosDesde(UUID idUsuario, Instant desde) {
			return (int) guardados.stream()
					.filter(g -> g.idUsuario().equals(idUsuario) && !g.emision().isBefore(desde)).count();
		}

		@Override
		public void borrarVencidos(UUID idUsuario, Instant antesDe) {
			guardados.removeIf(g -> g.idUsuario().equals(idUsuario) && g.expiraEn().isBefore(antesDe));
		}

		@Override
		public Optional<TokenActivacion> buscarUltimoSinUsar(String hashToken) {
			for (int i = guardados.size() - 1; i >= 0; i--) {
				Guardado g = guardados.get(i);
				if (g.hash().equals(hashToken)) {
					boolean esElUltimo = guardados.subList(i + 1, guardados.size()).stream()
							.noneMatch(otro -> otro.idUsuario().equals(g.idUsuario()));
					return esElUltimo && !g.usado() ? Optional.of(new TokenActivacion(g.id(), g.idUsuario(),
							g.expiraEn())) : Optional.empty();
				}
			}
			return Optional.empty();
		}

		@Override
		public boolean marcarUsado(UUID idToken, Instant fechaUso) {
			for (int i = 0; i < guardados.size(); i++) {
				Guardado g = guardados.get(i);
				if (g.id().equals(idToken) && !g.usado()) {
					guardados.set(i, new Guardado(g.id(), g.idUsuario(), g.hash(), g.emision(), g.expiraEn(), true));
					return true;
				}
			}
			return false;
		}

	}

	/** Secretos numerados ("secreto-1", "secreto-2"…) con hash "hash-" + secreto. */
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

	/** Reloj que las pruebas pueden adelantar. */
	private static final class RelojMovible extends Clock {

		private Instant ahora;

		RelojMovible(Instant inicio) {
			this.ahora = inicio;
		}

		void adelantar(Duration cuanto) {
			ahora = ahora.plus(cuanto);
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

}
