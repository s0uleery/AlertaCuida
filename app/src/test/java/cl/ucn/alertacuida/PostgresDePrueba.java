package cl.ucn.alertacuida;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL en Docker para las pruebas que levantan la aplicación completa.
 * Spring toma de aquí la conexión, así que no hace falta una base instalada.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDePrueba {

	/** Misma versión que el servidor (ver Versiones.md). */
	public static final String IMAGEN = "postgres:18-alpine";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(IMAGEN);
	}

}
