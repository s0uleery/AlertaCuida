package cl.ucn.alertacuida.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Primer administrador de un servidor sin datos de ejemplo (alertacuida.administrador-inicial en
 * application.properties, variables ADMIN_INICIAL_*). Vacío en desarrollo y en las pruebas: ahí no se crea
 * nada. Sin validación de Spring porque es opcional; la revisa {@link AdministradorInicialAlArrancar}.
 *
 * @param run    RUN del administrador, con o sin puntos; vacío = no crear ninguno
 * @param nombre nombre que se muestra en el dashboard
 * @param correo correo de la cuenta
 */
@ConfigurationProperties("alertacuida.administrador-inicial")
public record PropiedadesAdministradorInicial(String run, String nombre, String correo) {

	/** @return si la configuración pide crear el administrador inicial */
	public boolean configurado() {
		return run != null && !run.isBlank();
	}

}
