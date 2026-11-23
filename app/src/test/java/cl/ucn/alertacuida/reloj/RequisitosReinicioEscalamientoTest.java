package cl.ucn.alertacuida.reloj;

import static cl.ucn.alertacuida.soporte.EscenarioEscalamiento.LUNES_MEDIODIA;
import static cl.ucn.alertacuida.soporte.EscenarioEscalamiento.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.AlertaCuidaApplication;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.application.aviso.EnviarCorreosPendientes;
import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.escalamiento.VigilarEscalamiento;
import cl.ucn.alertacuida.soporte.EscenarioEscalamiento;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import cl.ucn.alertacuida.soporte.RelojAjustable;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Reinicio del servidor a mitad de un escalamiento: la aplicación completa se levanta, el reloj envía una
 * alerta y pide el primer aviso, y la aplicación se apaga antes de que salga el correo. Al levantarla de nuevo
 * contra la misma base, el escalamiento sigue sin perder ni repetir nada. Es lo que pide la tarea del
 * escalamiento del cronograma ("pruebas ... y un reinicio del servidor a mitad de un escalamiento"). Habla con
 * el servidor por HTTP, como el reloj. Sin Docker se omite.
 * <ul>
 * <li>RS1 El estado de la alerta y el aviso pedido sobreviven al reinicio.</li>
 * <li>RS2 El correo que quedó pendiente sale después del reinicio.</li>
 * <li>RS3 Si el reloj reenvía la solicitud porque no supo si llegó, no se crea otro aviso.</li>
 * <li>RS4 El vigilante cuenta desde el último aviso guardado, no desde que arrancó el servidor.</li>
 * <li>RS5 Una confirmación después del reinicio detiene al reloj.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class RequisitosReinicioEscalamientoTest {

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresDePrueba.IMAGEN);

	private final RelojAjustable reloj = new RelojAjustable(LUNES_MEDIODIA);
	private final HttpClient http = HttpClient.newHttpClient();
	private final UUID eventAlerta = UUID.randomUUID();

	private ConfigurableApplicationContext servidor;

	/** Apaga el servidor si la prueba terminó con él levantado. */
	@AfterEach
	void apagar() {
		if (servidor != null) {
			servidor.close();
		}
	}

	/** RS1 a RS5: se apaga con el correo de la hija pendiente y, al volver, el recorrido sigue y termina. */
	@Test
	void elEscalamientoSigueDespuesDeUnReinicio() throws Exception {
		servidor = arrancar();
		EscenarioEscalamiento escenario = escenario();
		escenario.preparar();
		UUID hija = escenario.hija();
		UUID vecino = escenario.vecino();
		assertThat(enviar("/api/reloj/v1/alertas", EscenarioEscalamiento.alerta(eventAlerta, reloj.instant()))
				.statusCode()).isEqualTo(201);
		reloj.adelantar(Duration.ofMinutes(10));
		UUID solicitudHija = UUID.randomUUID();
		UUID avisoHija = aceptado(pedirAviso(solicitudHija, hija, 1));
		assertThat(escenario.enviado(avisoHija)).as("el correo aún no sale").isFalse();

		servidor.close();
		reloj.adelantar(Duration.ofMinutes(2));
		servidor = arrancar();
		escenario = escenario();

		HttpResponse<String> estado = estado();
		assertThat(JsonPath.<String>read(estado.body(), "$.estado")).isEqualTo("AVISANDO_CONTACTO");
		assertThat(JsonPath.<Boolean>read(estado.body(), "$.detenerEscalamiento")).isFalse();
		assertThat(aceptado(pedirAviso(solicitudHija, hija, 1))).isEqualTo(avisoHija);
		assertThat(escenario.avisosA(eventAlerta, hija)).isEqualTo(1);

		servidor.getBean(EnviarCorreosPendientes.class).enviar();
		assertThat(escenario.enviado(avisoHija)).as("el correo sale tras el reinicio").isTrue();

		reloj.adelantar(Duration.ofMinutes(8));
		servidor.getBean(VigilarEscalamiento.class).vigilar();
		assertThat(escenario.correosDelVigilante(eventAlerta)).as("20 min desde la alerta, 10 desde el aviso")
				.isZero();

		aceptado(pedirAviso(UUID.randomUUID(), vecino, 1));
		String token = EscenarioEscalamiento.tokenDelCorreo(servidor.getBean(BandejaSalida.class),
				servidor.getBean(PlantillasCorreo.class), reloj.instant(), "vecino@correo.cl");
		servidor.getBean(EnviarCorreosPendientes.class).enviar();
		HttpResponse<String> respuesta = enviarSinToken("/api/respuestas-contacto",
				EscenarioEscalamiento.respuesta(token, "VOY_EN_CAMINO"));
		assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);

		estado = estado();
		assertThat(JsonPath.<String>read(estado.body(), "$.estado")).isEqualTo("CONFIRMADA");
		assertThat(JsonPath.<Boolean>read(estado.body(), "$.detenerEscalamiento")).isTrue();
		assertThat(escenario.avisosConfirmadaAlReloj(eventAlerta)).isEqualTo(1);
	}

	/** @return la aplicación completa levantada contra la base de la prueba, con el reloj de la prueba */
	private ConfigurableApplicationContext arrancar() {
		return new SpringApplicationBuilder(AlertaCuidaApplication.class)
				.initializers(contexto -> ((GenericApplicationContext) contexto).registerBean("relojDePrueba",
						Clock.class, () -> reloj, definicion -> definicion.setPrimary(true)))
				.run("--server.port=0", "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
						"--spring.datasource.username=" + POSTGRES.getUsername(),
						"--spring.datasource.password=" + POSTGRES.getPassword());
	}

	/**
	 * @return el escenario con los componentes del servidor que está levantado ahora; solo conoce a la hija y
	 *         al vecino si él mismo preparó la base
	 */
	private EscenarioEscalamiento escenario() {
		return new EscenarioEscalamiento(servidor.getBean(JdbcClient.class), servidor.getBean(CifradoCampos.class),
				servidor.getBean(SecretosUnSoloUso.class));
	}

	/** El reloj pide avisar por correo a un contacto de la alerta. */
	private HttpResponse<String> pedirAviso(UUID eventId, UUID contacto, int ronda) throws Exception {
		return enviar("/api/reloj/v1/alertas/" + eventAlerta + "/avisos",
				EscenarioEscalamiento.solicitud(eventId, contacto, ronda));
	}

	/** @return la respuesta del servidor al estado de la alerta, que debe ser 200 */
	private HttpResponse<String> estado() throws Exception {
		HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(ruta("/api/reloj/v1/alertas/" + eventAlerta))
				.header("Authorization", "Bearer " + TOKEN).GET().build(), HttpResponse.BodyHandlers.ofString());
		assertThat(respuesta.statusCode()).isEqualTo(200);
		return respuesta;
	}

	/** @return la respuesta del servidor a un POST con cuerpo JSON y el token del reloj */
	private HttpResponse<String> enviar(String camino, String json) throws Exception {
		return http.send(post(camino, json).header("Authorization", "Bearer " + TOKEN).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	/** @return la respuesta del servidor a un POST con cuerpo JSON sin token, como la página pública del contacto */
	private HttpResponse<String> enviarSinToken(String camino, String json) throws Exception {
		return http.send(post(camino, json).build(), HttpResponse.BodyHandlers.ofString());
	}

	/** @return un POST con cuerpo JSON a ese camino del servidor levantado ahora */
	private HttpRequest.Builder post(String camino, String json) {
		return HttpRequest.newBuilder(ruta(camino)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json));
	}

	/** @return la dirección de ese camino en el servidor levantado ahora */
	private URI ruta(String camino) {
		return URI.create("http://localhost:" + servidor.getEnvironment().getProperty("local.server.port") + camino);
	}

	/** @return el idIntento de una solicitud aceptada */
	private static UUID aceptado(HttpResponse<String> respuesta) {
		assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
		assertThat(JsonPath.<String>read(respuesta.body(), "$.resultado")).isEqualTo("ACEPTADO");
		return UUID.fromString(JsonPath.read(respuesta.body(), "$.idIntento"));
	}

}
