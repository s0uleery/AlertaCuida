package cl.ucn.alertacuida.application.personal;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import cl.ucn.alertacuida.application.personal.modelo.CuentaPendiente;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import cl.ucn.alertacuida.application.personal.modelo.EnvioEnlace;
import cl.ucn.alertacuida.application.personal.modelo.NuevaCuenta;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.application.soporte.TextosCorreoDePrueba;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El enlace de activación sale por correo a la persona; si no sale, queda para que el administrador lo pase. */
class EnviarEnlaceActivacionTest {

	private static final String URL = "https://alertacuida.muni.cl/activar";
	/** 25-11-2026 a las 15:30 en Chile (verano, UTC-3). */
	private static final Instant VENCE = Instant.parse("2026-11-25T18:30:00Z");

	private final Map<UUID, CuentaPendiente> pendientes = new HashMap<>();
	private final List<Mensaje> enviados = new ArrayList<>();

	/** Con un canal real, el correo sale a la persona con su nombre, el enlace y el vencimiento en hora de Chile. */
	@Test
	void conUnCanalRealElCorreoSaleALaPersona() {
		UUID ana = pendiente("Ana Pérez", "ana@muni.cl");

		EnvioEnlace envio = enviarCon(canal(true)).enviar(new EnlaceActivacion(ana, "tok-123", VENCE));

		assertThat(envio.correoEnviado()).isTrue();
		assertThat(envio.enlace()).isEqualTo(URL + "#tok-123");
		assertThat(enviados).singleElement().satisfies(mensaje -> {
			assertThat(mensaje.destinatario()).isEqualTo("ana@muni.cl");
			assertThat(mensaje.asunto()).isEqualTo("Active su cuenta, Ana Pérez");
			assertThat(mensaje.texto()).contains("Hola, Ana Pérez.", URL + "#tok-123", "25-11-2026 a las 15:30",
					"Pie del equipo.");
			assertThat(mensaje.importante()).isFalse();
		});
		assertThat(envio.toString()).doesNotContain("tok-123");
	}

	/** Con el canal simulado, el correo se registra pero no llega: el enlace debe mostrarse al administrador. */
	@Test
	void conElCanalSimuladoNoCuentaComoEnviado() {
		UUID ana = pendiente("Ana Pérez", "ana@muni.cl");

		EnvioEnlace envio = enviarCon(canal(false)).enviar(new EnlaceActivacion(ana, "tok-123", VENCE));

		assertThat(enviados).hasSize(1);
		assertThat(envio.correoEnviado()).isFalse();
	}

	/** Si el servidor de correo falla, no se propaga el error (la cuenta ya existe) y queda como no enviado. */
	@Test
	void siElCorreoFallaLaCuentaSigueYQuedaComoNoEnviado() {
		UUID ana = pendiente("Ana Pérez", "ana@muni.cl");
		CanalNotificacion caido = mensaje -> {
			throw new EnvioFallido("Servidor de correo caído", null);
		};

		EnvioEnlace envio = enviarCon(caido).enviar(new EnlaceActivacion(ana, "tok-123", VENCE));

		assertThat(envio.correoEnviado()).isFalse();
		assertThat(envio.enlace()).isEqualTo(URL + "#tok-123");
	}

	/** Una cuenta que ya no está pendiente (se activó entre medio) no recibe correo. */
	@Test
	void unaCuentaQueYaNoEstaPendienteNoRecibeCorreo() {
		EnvioEnlace envio = enviarCon(canal(true)).enviar(new EnlaceActivacion(UUID.randomUUID(), "tok", VENCE));

		assertThat(envio.correoEnviado()).isFalse();
		assertThat(enviados).isEmpty();
	}

	/** @return una cuenta pendiente con ese nombre y correo */
	private UUID pendiente(String nombre, String correo) {
		UUID id = UUID.randomUUID();
		pendientes.put(id, new CuentaPendiente(id, "hash-run", nombre, correo));
		return id;
	}

	/** @return un canal que guarda lo enviado; entregaDeVerdad según el parámetro */
	private CanalNotificacion canal(boolean deVerdad) {
		return new CanalNotificacion() {
			@Override
			public String enviar(Mensaje mensaje) {
				enviados.add(mensaje);
				return "id-" + enviados.size();
			}

			@Override
			public boolean entregaDeVerdad() {
				return deVerdad;
			}
		};
	}

	/** @return el caso de uso con las cuentas pendientes de la prueba y los textos de prueba */
	private EnviarEnlaceActivacion enviarCon(CanalNotificacion canal) {
		FirmaRespuesta firma = new FirmaRespuesta() {
			@Override
			public String firmar(UUID idIntento) {
				return "firma";
			}

			@Override
			public Optional<UUID> verificar(String token) {
				return Optional.empty();
			}
		};
		PlantillasCorreo plantillas = new PlantillasCorreo("https://alertacuida.muni.cl",
				TextosCorreoDePrueba.textos(), firma, Duration.ofMinutes(15));
		return new EnviarEnlaceActivacion(soloPendientes(), plantillas, canal, URL);
	}

	/** @return un doble de CuentasPersonal que solo responde buscarPendiente */
	private CuentasPersonal soloPendientes() {
		return new CuentasPersonal() {
			@Override
			public Optional<CuentaPendiente> buscarPendiente(UUID id) {
				return Optional.ofNullable(pendientes.get(id));
			}

			@Override
			public boolean existeRunHash(String runHash) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean existeCorreo(String correo) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean existeAdministrador() {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean rolActivo(String codigoRol) {
				throw new UnsupportedOperationException();
			}

			@Override
			public UUID crear(NuevaCuenta cuenta) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean activar(UUID id, String hashContrasena) {
				throw new UnsupportedOperationException();
			}
		};
	}

}
