package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.application.personal.CrearAdministradorInicial;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Al terminar de arrancar (después de las migraciones), crea el primer administrador si la configuración
 * trae su RUN y todavía no hay ninguno. El enlace de activación queda en el log del backend, para que la
 * persona elija su contraseña en el dashboard: es de un solo uso y vence como cualquier otro enlace.
 * Si ya hay un administrador no hace nada, así que puede quedar configurado sin riesgo.
 */
@Component
@EnableConfigurationProperties(PropiedadesAdministradorInicial.class)
public class AdministradorInicialAlArrancar implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(AdministradorInicialAlArrancar.class);

	private final CrearAdministradorInicial crearAdministrador;
	private final PropiedadesAdministradorInicial propiedades;
	private final String urlActivacion;

	/** Lo arma Spring, con la dirección de activación de alertacuida.seguridad.activacion.url. */
	@Autowired
	public AdministradorInicialAlArrancar(CrearAdministradorInicial crearAdministrador,
			PropiedadesAdministradorInicial propiedades, PropiedadesSeguridad seguridad) {
		this(crearAdministrador, propiedades, seguridad.activacion().url());
	}

	/**
	 * @param urlActivacion página del dashboard donde se elige la contraseña; el token va después de "#"
	 */
	public AdministradorInicialAlArrancar(CrearAdministradorInicial crearAdministrador,
			PropiedadesAdministradorInicial propiedades, String urlActivacion) {
		this.crearAdministrador = crearAdministrador;
		this.propiedades = propiedades;
		this.urlActivacion = urlActivacion;
	}

	/** Corre una vez por arranque; si la configuración está incompleta, detiene el arranque. */
	@Override
	public void run(ApplicationArguments argumentos) {
		crear().ifPresent(enlace -> LOG.warn(
				"Administrador inicial creado. Para elegir su contraseña, abrir este enlace (un solo uso): {}",
				enlace));
	}

	/**
	 * @return el enlace de activación si se creó la cuenta; vacío si no está configurado o ya hay un
	 *         administrador
	 * @throws IllegalStateException si viene el RUN pero falta el nombre o el correo
	 */
	public Optional<String> crear() {
		if (!propiedades.configurado()) {
			return Optional.empty();
		}
		exigir(propiedades.nombre(), "ADMIN_INICIAL_NOMBRE");
		exigir(propiedades.correo(), "ADMIN_INICIAL_CORREO");
		return crearAdministrador.crearSiFalta(propiedades.run(), propiedades.nombre(), propiedades.correo())
				.map(enlace -> urlActivacion + "#" + enlace.token());
	}

	/** Detiene el arranque si falta un dato, diciendo qué variable completar. */
	private static void exigir(String valor, String variable) {
		if (valor == null || valor.isBlank()) {
			throw new IllegalStateException("Falta " + variable + " para crear el administrador inicial");
		}
	}

}
