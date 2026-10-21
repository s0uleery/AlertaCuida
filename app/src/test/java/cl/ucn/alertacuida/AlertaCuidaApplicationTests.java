package cl.ucn.alertacuida;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Levanta la aplicación completa con un PostgreSQL de Docker. Sin Docker se omite (no falla). */
@SpringBootTest
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class AlertaCuidaApplicationTests {

	@Test
	void contextLoads() {
	}

}
