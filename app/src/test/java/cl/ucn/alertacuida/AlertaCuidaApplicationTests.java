package cl.ucn.alertacuida;

import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Levanta la aplicación completa con un PostgreSQL de Docker. Sin Docker se omite (no falla). */
@SpringBootTest
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class AlertaCuidaApplicationTests {

	/** El backend arranca completo (con todas sus piezas conectadas) sobre un PostgreSQL de prueba. */
	@Test
	void contextLoads() {
	}

}
