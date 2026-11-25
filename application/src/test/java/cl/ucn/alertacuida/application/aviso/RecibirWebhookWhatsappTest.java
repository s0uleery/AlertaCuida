package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.modelo.EventoWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.EstadosWhatsapp;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Lo que llega por el webhook de Meta, ya leído (guía "create webhook endpoint", 24-11-2026): los estados de
 * entrega de cada WhatsApp y los botones que presiona el contacto. Puede venir más de un evento por aviso, y
 * Meta reintenta: los duplicados no cambian nada y un estado desordenado no retrocede.
 */
class RecibirWebhookWhatsappTest {

	private static final Instant T1 = Instant.parse("2026-11-24T15:00:00Z");
	private static final Instant T2 = T1.plusSeconds(30);
	private static final UUID INTENTO = UUID.randomUUID();

	private final EstadosEnMemoria estados = new EstadosEnMemoria();
	private final List<String> respuestas = new ArrayList<>();
	private final RecibirWebhookWhatsapp recibir = new RecibirWebhookWhatsapp(estados,
			(intento, respuesta) -> respuestas.add(intento + " " + respuesta), enTransaccion());

	/** Entregado y leído llenan sus fechas; un "leído" sin "entregado" llena también la entrega. */
	@Test
	void losEstadosLlenanSusFechas() {
		estados.agregar("wamid.A");
		estados.agregar("wamid.B");

		recibir.procesar(List.of(estado("wamid.A", "delivered", T1), estado("wamid.A", "read", T2),
				estado("wamid.B", "read", T2)));

		assertThat(estados.entrega).containsEntry("wamid.A", T1).containsEntry("wamid.B", T2);
		assertThat(estados.lectura).containsEntry("wamid.A", T2).containsEntry("wamid.B", T2);
	}

	/** Un estado repetido o que llega después de uno más avanzado no cambia la fecha ya guardada. */
	@Test
	void unDuplicadoODesordenadoNoRetrocede() {
		estados.agregar("wamid.A");

		recibir.procesar(List.of(estado("wamid.A", "read", T2), estado("wamid.A", "delivered", T1)));
		recibir.procesar(List.of(estado("wamid.A", "read", T2.plusSeconds(60))));

		assertThat(estados.entrega).containsEntry("wamid.A", T2);
		assertThat(estados.lectura).containsEntry("wamid.A", T2);
	}

	/** Un "failed" deja el aviso fallido con el código y el título de Meta (y sale el correo de respaldo). */
	@Test
	void unFalloQuedaConElMotivoDeMeta() {
		estados.agregar("wamid.A");

		recibir.procesar(List.of(new EventoWhatsapp.Estado("wamid.A", "failed", T1, "131026 Message undeliverable")));

		assertThat(estados.fallo).containsEntry("wamid.A", "131026 Message undeliverable");
	}

	/** "sent" ya quedó al enviar, y un estado de un mensaje que no es nuestro se ignora. */
	@Test
	void sentYLosDesconocidosNoCambianNada() {
		estados.agregar("wamid.A");

		recibir.procesar(List.of(estado("wamid.A", "sent", T1), estado("wamid.OTRO", "delivered", T1)));

		assertThat(estados.entrega).isEmpty();
		assertThat(estados.fallo).isEmpty();
	}

	/** El botón se vincula con el aviso por el mensaje que lo contenía, y su payload es la respuesta. */
	@Test
	void elBotonResponde() {
		estados.agregar("wamid.A", INTENTO);

		recibir.procesar(List.of(new EventoWhatsapp.Boton("wamid.A", "VOY_EN_CAMINO"),
				new EventoWhatsapp.Boton("wamid.A", "NO_PUEDO")));

		// Los dos llegan a la respuesta: vale la primera, y eso lo decide ResponderAviso
		assertThat(respuestas).containsExactly(INTENTO + " VOY_EN_CAMINO", INTENTO + " NO_PUEDO");
	}

	/** Un botón de un mensaje desconocido o con un payload que no es nuestro se ignora. */
	@Test
	void unBotonAjenoSeIgnora() {
		estados.agregar("wamid.A", INTENTO);

		recibir.procesar(List.of(new EventoWhatsapp.Boton("wamid.OTRO", "VOY_EN_CAMINO"),
				new EventoWhatsapp.Boton("wamid.A", "Unsubscribe")));

		assertThat(respuestas).isEmpty();
	}

	/** @return un estado sin motivo */
	private static EventoWhatsapp estado(String id, String estado, Instant fecha) {
		return new EventoWhatsapp.Estado(id, estado, fecha, null);
	}

	/** @return transacciones que solo ejecutan */
	private static Transacciones enTransaccion() {
		return Supplier::get;
	}

	/** Avisos por WhatsApp en memoria, con las mismas condiciones que las sentencias SQL. */
	private static final class EstadosEnMemoria implements EstadosWhatsapp {

		final Map<String, UUID> intentos = new HashMap<>();
		final Map<String, Instant> entrega = new HashMap<>();
		final Map<String, Instant> lectura = new HashMap<>();
		final Map<String, String> fallo = new HashMap<>();

		void agregar(String idExterno) {
			agregar(idExterno, UUID.randomUUID());
		}

		void agregar(String idExterno, UUID intento) {
			intentos.put(idExterno, intento);
		}

		@Override
		public boolean registrarEntrega(String idExterno, Instant fecha) {
			return intentos.containsKey(idExterno) && entrega.putIfAbsent(idExterno, fecha) == null;
		}

		@Override
		public boolean registrarLectura(String idExterno, Instant fecha) {
			if (!intentos.containsKey(idExterno)) {
				return false;
			}
			entrega.putIfAbsent(idExterno, fecha);
			return lectura.putIfAbsent(idExterno, fecha) == null;
		}

		@Override
		public boolean registrarFallo(String idExterno, String motivo, Instant fecha) {
			return intentos.containsKey(idExterno) && fallo.putIfAbsent(idExterno, motivo) == null;
		}

		@Override
		public Optional<UUID> intentoPorIdExterno(String idExterno) {
			return Optional.ofNullable(intentos.get(idExterno));
		}

	}

}
