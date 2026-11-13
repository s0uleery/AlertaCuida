package cl.ucn.alertacuida.adapters.fcm;

import cl.ucn.alertacuida.application.avisoreloj.error.TokenRelojRechazado;
import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.CanalAvisoReloj;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * Entrega los avisos al simulador de reloj por HTTP local, en lugar de Firebase. Solo para desarrollo y
 * pruebas: un script no puede recibir mensajes de Firebase. El cuerpo lleva el token del reloj (así el
 * simulador sabe a cuál de sus relojes va) y los mismos datos que irían por Firebase.
 */
public final class CanalAvisoRelojSimulador implements CanalAvisoReloj {

	/** Plazo corto: el simulador corre en el mismo computador y no debe frenar la tarea programada. */
	private static final Duration PLAZO = Duration.ofSeconds(5);

	private final URI direccion;
	private final ObjectMapper json;
	private final HttpClient cliente;

	/**
	 * @param direccion dónde escucha el simulador (alertacuida.fcm.simulador-url)
	 * @param json      para armar el cuerpo
	 */
	public CanalAvisoRelojSimulador(URI direccion, ObjectMapper json) {
		this.direccion = direccion;
		this.json = json;
		this.cliente = HttpClient.newBuilder().connectTimeout(PLAZO).build();
	}

	/**
	 * @throws TokenRelojRechazado    si el simulador responde 404 o 410: no tiene un reloj con ese token
	 * @throws IllegalStateException si responde otro error o no se pudo conectar: se reintentará
	 */
	@Override
	public void enviar(AvisoReloj aviso) {
		String cuerpo = json.writeValueAsString(Map.of("token", aviso.token(), "datos", aviso.datos()));
		HttpRequest pedido = HttpRequest.newBuilder(direccion)
				.timeout(PLAZO)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(cuerpo))
				.build();
		int estado = responder(pedido);
		if (estado == 404 || estado == 410) {
			throw new TokenRelojRechazado(new IllegalStateException("El simulador no conoce el reloj: " + estado));
		}
		if (estado < 200 || estado >= 300) {
			throw new IllegalStateException("El simulador no aceptó el aviso: " + estado);
		}
	}

	/** @return el código de respuesta del simulador */
	private int responder(HttpRequest pedido) {
		try {
			return cliente.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo conectar con el simulador de reloj en " + direccion, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Se interrumpió el envío al simulador de reloj", e);
		}
	}

}
