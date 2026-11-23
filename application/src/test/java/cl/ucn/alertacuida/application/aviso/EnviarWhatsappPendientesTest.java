package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.error.CanalNoConfigurado;
import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.aviso.puerto.CanalWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.RegistroEnvios;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Envío de los WhatsApp que pidió el reloj, sin base de datos. Decisión del 13-11-2026: mientras el canal
 * de WhatsApp no esté configurado (antes de la 2.9), el aviso falla de inmediato, sin reintentos, para que
 * salga enseguida el correo de respaldo. Con el canal real, una falla se reintenta como los correos.
 */
class EnviarWhatsappPendientesTest {

	private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
	private static final PoliticaReintentos POLITICA = new PoliticaReintentos(
			List.of(Duration.ofMinutes(1), Duration.ofMinutes(5)));

	private final List<String> registro = new ArrayList<>();

	/** Sin canal configurado, el aviso queda fallido al tiro y no se reprograma. */
	@Test
	void sinCanalConfiguradoFallaAlTiroSinReintentos() {
		WhatsappPendiente aviso = pendiente("+56911111111", 0);

		enviarCon(m -> {
			throw new CanalNoConfigurado("WhatsApp aún no está configurado");
		}, aviso);

		assertThat(registro).containsExactly("fallido " + aviso.idIntento() + ": WhatsApp aún no está configurado");
	}

	/** Con el canal real, lo enviado queda con el id del mensaje. */
	@Test
	void loEnviadoQuedaConSuIdExterno() {
		WhatsappPendiente aviso = pendiente("+56911111111", 0);

		enviarCon(m -> "wamid.123", aviso);

		assertThat(registro).containsExactly("enviado " + aviso.idIntento() + " wamid.123");
	}

	/** Una falla del canal real se reintenta según la política; agotada, queda fallido. */
	@Test
	void unaFallaSeReintentaYAgotadaQuedaFallida() {
		WhatsappPendiente primera = pendiente("+56911111111", 0);
		WhatsappPendiente agotada = pendiente("+56922222222", 2);

		enviarCon(m -> {
			throw new EnvioFallido("Meta respondió 500");
		}, primera, agotada);

		assertThat(registro).containsExactly(
				"reprogramado " + primera.idIntento() + " 1 " + AHORA.plus(Duration.ofMinutes(1)),
				"fallido " + agotada.idIntento() + ": Meta respondió 500");
	}

	/** Un contacto que ya no tiene número queda fallido sin intentar enviar. */
	@Test
	void sinNumeroQuedaFallidoSinEnviar() {
		WhatsappPendiente aviso = pendiente(null, 0);
		List<WhatsappPendiente> enviados = new ArrayList<>();

		enviarCon(m -> {
			enviados.add(m);
			return "x";
		}, aviso);

		assertThat(enviados).isEmpty();
		assertThat(registro).singleElement().asString().startsWith("fallido " + aviso.idIntento());
	}

	/** Envía con ese canal los avisos dados y deja lo ocurrido en el registro. */
	private void enviarCon(CanalWhatsapp canal, WhatsappPendiente... avisos) {
		RegistroEnvios envios = new RegistroEnvios() {
			@Override
			public void marcarEnviado(UUID id, String idExterno, Instant ahora) {
				registro.add("enviado " + id + " " + idExterno);
			}

			@Override
			public void reprogramar(UUID id, int reintentos, Instant proximo) {
				registro.add("reprogramado " + id + " " + reintentos + " " + proximo);
			}

			@Override
			public void marcarFallido(UUID id, String motivo, Instant ahora) {
				registro.add("fallido " + id + ": " + motivo);
			}
		};
		new EnviarWhatsappPendientes(ahora -> List.of(avisos), envios, canal, POLITICA,
				Clock.fixed(AHORA, ZoneOffset.UTC)).enviar();
	}

	/** @return un aviso pendiente de una caída grave */
	private static WhatsappPendiente pendiente(String telefono, int reintentos) {
		return new WhatsappPendiente(UUID.randomUUID(), telefono, 142, "Rosa", TipoEvento.CAIDA, Severidad.GRAVE,
				false, reintentos);
	}

}
