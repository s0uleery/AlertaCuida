package cl.ucn.alertacuida.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * La auditoría guarda la IP del navegador y no la de nginx. Usa un servidor real, porque es Tomcat
 * quien interpreta X-Forwarded-For. Sin Docker se omite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class IpRealTest {

	private static final String IP_NAVEGADOR = "203.0.113.7";

	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private Environment entorno;

	/** Una petición reenviada por el proxy local queda registrada con la IP que informó el proxy. */
	@Test
	void laAuditoriaGuardaLaIpQueInformaElProxy() throws Exception {
		jdbc.sql("DELETE FROM registro_auditoria").update();
		HttpRequest peticion = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + entorno.getProperty("local.server.port") + "/api/auth/login"))
				.header("Content-Type", "application/json")
				.header("X-Forwarded-For", IP_NAVEGADOR)
				.POST(HttpRequest.BodyPublishers.ofString("{\"run\": \"44.444.444-4\", \"contrasena\": \"x\"}"))
				.build();

		HttpResponse<Void> respuesta = HttpClient.newHttpClient()
				.send(peticion, HttpResponse.BodyHandlers.discarding());

		assertThat(respuesta.statusCode()).isEqualTo(401);
		assertThat(jdbc.sql("SELECT ip FROM registro_auditoria WHERE accion = 'LOGIN_FALLIDO'")
				.query(String.class).list()).containsExactly(IP_NAVEGADOR);
	}

}
