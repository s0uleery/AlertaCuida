package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Envío de los correos pendientes, sin base de datos ni servidor de correo. Requisitos (decisión
 * del 08-11-2026 y cronograma):
 * <ul>
 * <li>Si el envío falla, se reintenta 3 veces: a los 1, 5 y 15 minutos.</li>
 * <li>Después queda marcado como fallido, con el motivo, para que el equipo lo vea.</li>
 * <li>Un correo que falla no detiene el envío de los demás.</li>
 * </ul>
 */
class EnviarCorreosPendientesTest {

	private static final Instant AHORA = Instant.parse("2026-11-09T15:00:00Z");
	private static final PoliticaReintentos POLITICA = new PoliticaReintentos(
			List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)));

	private final BandejaEnMemoria bandeja = new BandejaEnMemoria();
	private final List<Mensaje> enviados = new ArrayList<>();
	/** Destinatarios cuyo servidor de correo rechaza el mensaje. */
	private final Set<String> fallan = new HashSet<>();

	private final CanalNotificacion canal = mensaje -> {
		if (fallan.contains(mensaje.destinatario())) {
			throw new EnvioFallido("Servidor de correo no disponible");
		}
		enviados.add(mensaje);
		return "id-" + enviados.size();
	};

	private final EnviarCorreosPendientes enviar = new EnviarCorreosPendientes(bandeja, canal,
			new PlantillasCorreo("https://dashboard"), POLITICA, Clock.fixed(AHORA, ZoneOffset.UTC));

	/** Un correo que sale bien queda enviado, con el identificador que devolvió el servidor. */
	@Test
	void unCorreoEnviadoQuedaRegistrado() {
		CorreoPendiente correo = bandeja.agregar("ana@muni.cl", 0);

		enviar.enviar();

		assertThat(enviados).extracting(Mensaje::destinatario).containsExactly("ana@muni.cl");
		assertThat(bandeja.enviados).containsEntry(correo.idIntento(), "id-1");
	}

	/** Primer fallo: se reintenta al minuto. */
	@Test
	void primerFalloReintentaAlMinuto() {
		CorreoPendiente correo = bandeja.agregar("ana@muni.cl", 0);
		fallan.add("ana@muni.cl");

		enviar.enviar();

		assertThat(bandeja.reprogramados).containsExactly(
				new Reprogramado(correo.idIntento(), 1, AHORA.plus(Duration.ofMinutes(1))));
		assertThat(bandeja.fallidos).isEmpty();
	}

	/** Segundo y tercer fallo: a los 5 y a los 15 minutos. */
	@Test
	void losReintentosSiguientesEsperan5Y15Minutos() {
		CorreoPendiente segundo = bandeja.agregar("a@muni.cl", 1);
		CorreoPendiente tercero = bandeja.agregar("b@muni.cl", 2);
		fallan.add("a@muni.cl");
		fallan.add("b@muni.cl");

		enviar.enviar();

		assertThat(bandeja.reprogramados).containsExactlyInAnyOrder(
				new Reprogramado(segundo.idIntento(), 2, AHORA.plus(Duration.ofMinutes(5))),
				new Reprogramado(tercero.idIntento(), 3, AHORA.plus(Duration.ofMinutes(15))));
	}

	/** Tras los 3 reintentos, queda fallido con el motivo, sin más reintentos. */
	@Test
	void despuesDeTresReintentosQuedaFallido() {
		CorreoPendiente correo = bandeja.agregar("ana@muni.cl", 3);
		fallan.add("ana@muni.cl");

		enviar.enviar();

		assertThat(bandeja.fallidos).containsEntry(correo.idIntento(), "Servidor de correo no disponible");
		assertThat(bandeja.reprogramados).isEmpty();
	}

	/** Sin correo de destino no hay nada que reintentar: queda fallido de inmediato. */
	@Test
	void sinCorreoDeDestinoQuedaFallido() {
		CorreoPendiente correo = bandeja.agregar(null, 0);

		enviar.enviar();

		assertThat(enviados).isEmpty();
		assertThat(bandeja.fallidos).containsKey(correo.idIntento());
	}

	/** Un correo que falla no detiene a los demás. */
	@Test
	void unFalloNoDetieneALosDemas() {
		bandeja.agregar("falla@muni.cl", 0);
		bandeja.agregar("ana@muni.cl", 0);
		fallan.add("falla@muni.cl");

		enviar.enviar();

		assertThat(enviados).extracting(Mensaje::destinatario).containsExactly("ana@muni.cl");
	}

	/** Un error inesperado del canal (no un rechazo del correo) tampoco detiene a los demás, y se reintenta. */
	@Test
	void unErrorInesperadoSeReintenta() {
		CorreoPendiente roto = bandeja.agregar("roto@muni.cl", 0);
		bandeja.agregar("ana@muni.cl", 0);
		EnviarCorreosPendientes conError = new EnviarCorreosPendientes(bandeja, mensaje -> {
			if (mensaje.destinatario().startsWith("roto")) {
				throw new IllegalStateException("error inesperado");
			}
			enviados.add(mensaje);
			return "id";
		}, new PlantillasCorreo("https://dashboard"), POLITICA, Clock.fixed(AHORA, ZoneOffset.UTC));

		conError.enviar();

		assertThat(enviados).hasSize(1);
		assertThat(bandeja.reprogramados).extracting(Reprogramado::idIntento).containsExactly(roto.idIntento());
	}

	/** Un reintento reprogramado. */
	private record Reprogramado(UUID idIntento, int reintentos, Instant proximo) {
	}

	/** Bandeja de salida en memoria: guarda lo que el caso de uso marca en cada correo. */
	private static final class BandejaEnMemoria implements BandejaSalida {

		private final List<CorreoPendiente> pendientes = new ArrayList<>();
		private final Map<UUID, String> enviados = new HashMap<>();
		private final Map<UUID, String> fallidos = new HashMap<>();
		private final List<Reprogramado> reprogramados = new ArrayList<>();

		/** @return un correo grave al equipo, con esos reintentos ya hechos */
		CorreoPendiente agregar(String destinatario, int reintentos) {
			CorreoPendiente correo = new CorreoPendiente(UUID.randomUUID(), destinatario, true, 7, "Rosa",
					TipoEvento.CAIDA, Severidad.GRAVE, reintentos);
			pendientes.add(correo);
			return correo;
		}

		@Override
		public List<CorreoPendiente> pendientes(Instant ahora) {
			return List.copyOf(pendientes);
		}

		@Override
		public void marcarEnviado(UUID idIntento, String idExterno, Instant ahora) {
			enviados.put(idIntento, idExterno);
		}

		@Override
		public void reprogramar(UUID idIntento, int reintentos, Instant proximo) {
			reprogramados.add(new Reprogramado(idIntento, reintentos, proximo));
		}

		@Override
		public void marcarFallido(UUID idIntento, String motivo, Instant ahora) {
			fallidos.put(idIntento, motivo);
		}

	}

}
