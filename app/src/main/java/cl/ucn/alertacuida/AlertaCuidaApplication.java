package cl.ucn.alertacuida;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de arranque del backend. Spring Boot busca los componentes desde este paquete hacia abajo
 * (adaptadores, controladores, configuración) y, al arrancar, Flyway aplica las migraciones.
 */
@SpringBootApplication
public class AlertaCuidaApplication {

	/** @param args argumentos de Spring, por ejemplo --spring.profiles.active=dev */
	public static void main(String[] args) {
		SpringApplication.run(AlertaCuidaApplication.class, args);
	}

}
